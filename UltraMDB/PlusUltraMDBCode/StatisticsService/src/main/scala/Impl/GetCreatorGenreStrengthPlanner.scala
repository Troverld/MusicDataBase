package Impl

import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import APIs.OrganizeService.validateUserMapping
import APIs.CreatorService.{GetArtistByID, GetBandByID}
import APIs.MusicService.{FilterSongsByEntity, GetSongProfile}
import APIs.StatisticsService.GetSongPopularity
import Objects.StatisticsService.Profile
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
 * @param creatorID   创作者ID
 * @param creatorType 创作者类型 ("Artist" 或 "Band")
 * @param planContext 执行上下文
 */
case class GetCreatorGenreStrengthPlanner(
                                           userID: String,
                                           userToken: String,
                                           creatorID: String,
                                           creatorType: String,
                                           override val planContext: PlanContext
                                         ) extends Planner[(Option[Profile], String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Option[Profile], String)] = {
    // 移除缓存逻辑，直接进行计算
    val logic: IO[Profile] = for {
      _ <- logInfo(s"开始获取创作者 ${creatorID} (${creatorType}) 的曲风实力")

      // 步骤1: 验证用户身份
      _ <- validateUser()

      // 步骤2: 验证创作者类型
      _ <- validateCreatorType()

      // 步骤3: 验证创作者存在性
      _ <- validateCreator()

      // 步骤4: 实时计算实力
      strength <- calculateStrength()

    } yield strength

    logic.map { strength =>
      (Some(strength), "获取创作实力成功")
    }.handleErrorWith { error =>
      logError(s"获取创作者 ${creatorID} 创作实力失败", error) >>
        IO.pure((None, error.getMessage))
    }
  }

  // --- Validation Steps (unchanged) ---

  private def validateUser()(using PlanContext): IO[Unit] = {
    logInfo("正在验证用户身份") >> {
      validateUserMapping(userID, userToken).send.flatMap { case (isValid, message) =>
        if (isValid) IO.unit else IO.raiseError(new IllegalArgumentException(s"用户身份验证失败: $message"))
      }
    }
  }

  private def validateCreatorType()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证创作者类型: ${creatorType}") >> {
      if (creatorType == "Artist" || creatorType == "Band") IO.unit
      else IO.raiseError(new IllegalArgumentException(s"无效的创作者类型: ${creatorType}，只支持 'Artist' 或 'Band'"))
    }
  }

  private def validateCreator()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证创作者 ${creatorID} 是否存在") >> {
      val validationIO = creatorType match {
        case "Artist" => GetArtistByID(userID, userToken, creatorID).send
        case "Band"   => GetBandByID(userID, userToken, creatorID).send
      }
      validationIO.flatMap {
        case (Some(_), _) => logInfo(s"${creatorType}存在性验证通过")
        case (None, msg)  => IO.raiseError(new IllegalStateException(s"${creatorType}不存在: $msg"))
      }
    }
  }

  // --- Calculation Logic ---

  /**
   * 步骤4: 实时计算创作实力
   */
  private def calculateStrength()(using PlanContext): IO[Profile] = {
    for {
      _ <- logInfo("开始实时计算创作实力")
      songs <- getCreatorSongs()
      _ <- logInfo(s"获取到创作者作品 ${songs.length} 首")

      strength <- if (songs.isEmpty) {
        logInfo("创作者暂无作品，返回空实力")
        IO.pure(Profile(List.empty, norm = false))
      } else {
        for {
          genreStrengths <- calculateGenreStrengths(songs)
          _ <- logInfo(s"计算出曲风实力: ${genreStrengths}")
        } yield Profile(genreStrengths, norm = false)
      }
    } yield strength
  }

  /**
   * 获取创作者的所有作品ID列表
   */
  private def getCreatorSongs()(using PlanContext): IO[List[String]] = {
    FilterSongsByEntity(
      userID = userID,
      userToken = userToken,
      entityID = Some(creatorID),
      entityType = Some(creatorType.toLowerCase)
    ).send.flatMap {
      case (Some(songs), _) => IO.pure(songs)
      case (None, message) =>
        logInfo(s"获取创作者作品失败: $message. 将视为空列表处理。") >> IO.pure(List.empty)
    }
  }

  /**
   * 计算各曲风的实力分数
   * 实力 = 该曲风下所有作品的平均热度
   */
  private def calculateGenreStrengths(songs: List[String])(using PlanContext): IO[List[(String, Double)]] = {
    for {
      // 1. 为每首歌获取其曲风和热度
      songData <- songs.traverse { songId =>
        fetchSongData(songId)
      }

      // 2. 按曲风分组计算平均热度
      genreStrengths = calculateAveragePopularityByGenre(songData)

    } yield genreStrengths
  }

  /**
   * 使用API获取单首歌曲的曲风列表和热度分数
   */
  private def fetchSongData(songId: String)(using PlanContext): IO[(List[String], Double)] = {
    for {
      // 并行调用两个API
      results <- (
        GetSongProfile(userID, userToken, songId).send,
        GetSongPopularity(userID, userToken, songId).send
      ).parTupled

      (profileResult, popularityResult) = results

      // 解析曲风
      genres = profileResult match {
        case (Some(profile), _) => profile.vector.map(_._1) // 提取GenreID
        case (None, msg) =>
          logger.warn(s"TID=${planContext.traceID.id} -- 获取歌曲 $songId 的Profile失败: $msg")
          List.empty[String]
      }

      // 解析热度
      popularity = popularityResult match {
        case (Some(score), _) => score
        case (None, msg) =>
          logger.warn(s"TID=${planContext.traceID.id} -- 获取歌曲 $songId 的Popularity失败: $msg")
          0.0
      }

    } yield (genres, popularity)
  }


  /**
   * 根据歌曲数据，按曲风计算平均热度
   */
  private def calculateAveragePopularityByGenre(
    songData: List[(List[String], Double)]
  ): List[(String, Double)] = {
    // 将每首歌的热度分配给其所有曲风 (Map step)
    val genrePopularities: List[(String, Double)] = for {
      (genres, popularity) <- songData if genres.nonEmpty && popularity > 0
      genre <- genres
    } yield (genre, popularity)

    // 按曲风分组并计算平均值 (Reduce step)
    genrePopularities
      .groupBy(_._1)
      .view
      .mapValues { popularities =>
        val scores = popularities.map(_._2)
        if (scores.isEmpty) 0.0 else scores.sum / scores.length
      }
      .toList
  }

  private def logInfo(message: String): IO[Unit] =
    IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))

  private def logError(message: String, cause: Throwable): IO[Unit] =
    IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}