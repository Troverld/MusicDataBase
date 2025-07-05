package Impl

import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import APIs.OrganizeService.validateUserMapping
import APIs.CreatorService.{GetArtistByID, GetBandByID}
import APIs.MusicService.{FilterSongsByEntity, GetSongProfile} // API is already imported
import APIs.StatisticsService.GetSongPopularity
import Objects.CreatorService.{CreatorID_Type, CreatorType}
import Objects.StatisticsService.{Dim, Profile}
import Utils.StatisticsUtils
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory

/**
 * Planner for GetCreatorGenreStrength: 获取创作者在各曲风下的创作实力
 *
 * @param userID      请求用户的ID
 * @param userToken   用户认证令牌
 * @param creator     创作者的智能ID对象
 * @param planContext 执行上下文
 */
case class GetCreatorGenreStrengthPlanner(
                                           userID: String,
                                           userToken: String,
                                           creator: CreatorID_Type,
                                           override val planContext: PlanContext
                                         ) extends Planner[(Option[Profile], String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Option[Profile], String)] = {
    val logic: IO[Profile] = for {
      _ <- logInfo(s"开始获取创作者 ${creator.id} (${creator.creatorType}) 的曲风实力")
      _ <- validateUser()
      _ <- validateCreator()
      strength <- calculateStrength()
    } yield strength

    logic.map { strength =>
      (Some(strength), "获取创作实力成功")
    }.handleErrorWith { error =>
      logError(s"获取创作者 ${creator.id} (${creator.creatorType}) 创作实力失败", error) >>
        IO.pure((None, error.getMessage))
    }
  }

  // --- Validation Steps ---

  private def validateUser()(using PlanContext): IO[Unit] = {
    logInfo("正在验证用户身份") >> {
      validateUserMapping(userID, userToken).send.flatMap { case (isValid, message) =>
        if (isValid) IO.unit else IO.raiseError(new IllegalArgumentException(s"用户身份验证失败: $message"))
      }
    }
  }

  private def validateCreator()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证创作者 ${creator.id} 是否存在") >> {
      val validationIO = creator.creatorType match {
        case CreatorType.Artist => GetArtistByID(userID, userToken, creator.id).send
        case CreatorType.Band   => GetBandByID(userID, userToken, creator.id).send
      }
      validationIO.flatMap {
        case (Some(_), _) => logInfo(s"${creator.creatorType}存在性验证通过")
        case (None, msg)  => IO.raiseError(new IllegalStateException(s"${creator.creatorType}不存在: $msg"))
      }
    }
  }

  // --- Calculation Logic ---

  private def calculateStrength()(using PlanContext): IO[Profile] = {
    for {
      _ <- logInfo("开始实时计算创作实力")
      songs <- getCreatorSongs()
      _ <- logInfo(s"获取到创作者作品 ${songs.length} 首")

      strengthProfile <- if (songs.isEmpty) {
        logInfo("创作者暂无作品，返回空实力")
        IO.pure(Profile(List.empty, norm = false))
      } else {
        for {
          genreStrengthDims <- calculateGenreStrengths(songs)
          _ <- logInfo(s"计算出曲风实力: ${genreStrengthDims}")
        } yield Profile(genreStrengthDims, norm = false)
      }
    } yield strengthProfile
  }

  /**
   * 获取创作者的所有作品ID列表
   * (已使用最新版本的 FilterSongsByEntity API)
   */
  private def getCreatorSongs()(using PlanContext): IO[List[String]] = {
    // **修正点**: 使用更新后的 FilterSongsByEntity API
    FilterSongsByEntity(
      userID = userID,
      userToken = userToken,
      creator = Some(creator) // 直接传递 creator 对象
    ).send.flatMap {
      case (Some(songs), _) => IO.pure(songs)
      case (None, message) =>
        logInfo(s"获取创作者作品失败: $message. 将视为空列表处理。") >> IO.pure(List.empty)
    }
  }

  /**
   * 计算各曲风的实力分数
   */
  private def calculateGenreStrengths(songs: List[String])(using PlanContext): IO[List[Dim]] = {
    for {
      songData <- songs.traverse(fetchSongData)
      genreStrengths = calculateAveragePopularityByGenre(songData)
    } yield genreStrengths
  }

  /**
   * 获取单首歌曲的曲风和热度
   */
  private def fetchSongData(songId: String)(using PlanContext): IO[(List[String], Double)] = {
    for {
      results <- (
        GetSongProfile(userID, userToken, songId).send,
        GetSongPopularity(userID, userToken, songId).send
      ).parTupled

      (profileResult, popularityResult) = results

      genres = profileResult match {
        case (Some(profile), _) => profile.vector.map(_.GenreID)
        case (None, msg) =>
          logger.warn(s"TID=${planContext.traceID.id} -- 获取歌曲 $songId 的Profile失败: $msg")
          List.empty[String]
      }

      popularity = popularityResult match {
        case (Some(score), _) => score
        case (None, msg) =>
          logger.warn(s"TID=${planContext.traceID.id} -- 获取歌曲 $songId 的Popularity失败: $msg")
          0.0
      }
    } yield (genres, popularity)
  }

  /**
   * 按曲风计算平均热度
   */
  private def calculateAveragePopularityByGenre(
    songData: List[(List[String], Double)]
  ): List[Dim] = {
    val genrePopularities: List[(String, Double)] = for {
      (genres, popularity) <- songData if genres.nonEmpty && popularity > 0
      genre <- genres
    } yield (genre, popularity)

    genrePopularities
      .groupBy(_._1)
      .view
      .mapValues { popularities =>
        val scores = popularities.map(_._2)
        if (scores.isEmpty) 0.0 else scores.sum / scores.length
      }
      .toList
      .map { case (genreId, avgPopularity) => Dim(genreId, avgPopularity) }
  }

  private def logInfo(message: String): IO[Unit] =
    IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))

  private def logError(message: String, cause: Throwable): IO[Unit] =
    IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}