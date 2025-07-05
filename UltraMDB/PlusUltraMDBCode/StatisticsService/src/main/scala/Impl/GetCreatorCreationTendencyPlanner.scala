package Impl

import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import APIs.OrganizeService.validateUserMapping
import APIs.CreatorService.{GetArtistByID, GetBandByID}
import APIs.MusicService.{FilterSongsByEntity, GetSongProfile}
import Objects.CreatorService.{CreatorID_Type, CreatorType}
import Objects.StatisticsService.{Dim, Profile}
import Utils.StatisticsUtils
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory

/**
 * Planner for GetCreatorCreationTendency: 获取创作者的创作倾向
 *
 * @param userID      请求用户的ID
 * @param userToken   用户认证令牌
 * @param creator     创作者的智能ID对象，封装了ID和类型
 * @param planContext 执行上下文
 */
case class GetCreatorCreationTendencyPlanner(
                                              userID: String,
                                              userToken: String,
                                              creator: CreatorID_Type,
                                              override val planContext: PlanContext
                                            ) extends Planner[(Option[Profile], String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Option[Profile], String)] = {
    val logic: IO[Profile] = for {
      _ <- logInfo(s"开始获取创作者 ${creator.id} (${creator.creatorType}) 的创作倾向")
      _ <- validateUser()
      _ <- validateCreator()
      tendency <- calculateTendency()
    } yield tendency

    logic.map { tendency =>
      (Some(tendency), "获取创作倾向成功")
    }.handleErrorWith { error =>
      logError(s"获取创作者 ${creator.id} (${creator.creatorType}) 创作倾向失败", error) >>
        IO.pure((None, error.getMessage))
    }
  }

  /**
   * 步骤1: 验证用户身份
   */
  private def validateUser()(using PlanContext): IO[Unit] = {
    logInfo("正在验证用户身份") >> {
      validateUserMapping(userID, userToken).send.flatMap { case (isValid, message) =>
        if (isValid) {
          logInfo("用户身份验证通过")
        } else {
          IO.raiseError(new IllegalArgumentException(s"用户身份验证失败: $message"))
        }
      }
    }
  }

  /**
   * 步骤3: 验证创作者存在性
   */
  private def validateCreator()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证创作者 ${creator.id} 是否存在") >> {
      creator.creatorType match {
        case CreatorType.Artist =>
          GetArtistByID(userID, userToken, creator.id).send.flatMap {
            case (Some(_), _) => logInfo("艺术家存在性验证通过")
            case (None, message) => IO.raiseError(new IllegalStateException(s"艺术家不存在: $message"))
          }
        case CreatorType.Band =>
          GetBandByID(userID, userToken, creator.id).send.flatMap {
            case (Some(_), _) => logInfo("乐队存在性验证通过")
            case (None, message) => IO.raiseError(new IllegalStateException(s"乐队不存在: $message"))
          }
      }
    }
  }

  /**
   * 步骤4: 实时计算创作倾向
   */
  private def calculateTendency()(using PlanContext): IO[Profile] = {
    for {
      _ <- logInfo("开始实时计算创作倾向")
      songs <- getCreatorSongs()
      _ <- logInfo(s"获取到创作者作品 ${songs.length} 首")

      profile <- if (songs.isEmpty) {
        logInfo("创作者暂无作品，返回空倾向")
        IO.pure(Profile(List.empty, norm = true))
      } else {
        for {
          unnormalizedProfile <- calculateGenreDistribution(songs)
          _ <- logInfo(s"计算出未归一化的曲风分布: ${unnormalizedProfile.vector}")
          normalizedProfile = StatisticsUtils.normalizeVector(unnormalizedProfile)
        } yield normalizedProfile
      }
    } yield profile
  }

  /**
   * 获取创作者的所有作品
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
   * 使用 GetSongProfile API 计算曲风分布，并返回一个未归一化的 Profile
   */
  private def calculateGenreDistribution(songs: List[String])(using PlanContext): IO[Profile] = {
    songs.traverse { songId =>
      GetSongProfile(userID, userToken, songId).send.map {
        case (Some(profile), _) => profile.vector
        case (None, message) =>
          logger.warn(s"TID=${planContext.traceID.id} -- 获取歌曲 ${songId} 的Profile失败: $message. 将跳过此歌曲.")
          List.empty[Dim]
      }
    }.map { listOfVectors =>
      val allDims = listOfVectors.flatten
      val genreCounts = allDims
        .groupBy(_.GenreID)
        .view.mapValues(dims => dims.map(_.value).sum)
        .toList
        .map { case (genreId, count) => Dim(genreId, count) }
      Profile(genreCounts, norm = false)
    }
  }

  private def logInfo(message: String): IO[Unit] =
    IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))

  private def logError(message: String, cause: Throwable): IO[Unit] =
    IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}