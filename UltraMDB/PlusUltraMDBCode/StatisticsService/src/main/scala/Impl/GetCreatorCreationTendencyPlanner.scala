package Impl

import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import APIs.OrganizeService.validateUserMapping
import APIs.CreatorService.{GetArtistByID, GetBandByID}
import APIs.MusicService.{FilterSongsByEntity, GetSongProfile} // 1. 导入新的API
import Objects.StatisticsService.Profile
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
 * @param creatorID   创作者ID
 * @param creatorType 创作者类型 ("Artist" 或 "Band")
 * @param planContext 执行上下文
 */
case class GetCreatorCreationTendencyPlanner(
                                              userID: String,
                                              userToken: String,
                                              creatorID: String,
                                              creatorType: String,
                                              override val planContext: PlanContext
                                            ) extends Planner[(Option[Profile], String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Option[Profile], String)] = {
    // 2. 简化核心逻辑，移除缓存相关步骤
    val logic: IO[Profile] = for {
      _ <- logInfo(s"开始获取创作者 ${creatorID} (${creatorType}) 的创作倾向")

      // 步骤1: 验证用户身份
      _ <- validateUser()

      // 步骤2: 验证创作者类型
      _ <- validateCreatorType()

      // 步骤3: 验证创作者存在性
      _ <- validateCreator()

      // 步骤4: 实时计算创作倾向
      tendency <- calculateTendency()

    } yield tendency

    logic.map { tendency =>
      (Some(tendency), "获取创作倾向成功")
    }.handleErrorWith { error =>
      logError(s"获取创作者 ${creatorID} 创作倾向失败", error) >>
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
   * 步骤2: 验证创作者类型
   */
  private def validateCreatorType()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证创作者类型: ${creatorType}") >> {
      if (creatorType == "Artist" || creatorType == "Band") {
        logInfo("创作者类型验证通过")
      } else {
        IO.raiseError(new IllegalArgumentException(s"无效的创作者类型: ${creatorType}，只支持 'Artist' 或 'Band'"))
      }
    }
  }

  /**
   * 步骤3: 验证创作者存在性
   */
  private def validateCreator()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证创作者 ${creatorID} 是否存在") >> {
      creatorType match {
        case "Artist" =>
          GetArtistByID(userID, userToken, creatorID).send.flatMap {
            case (Some(_), _) => logInfo("艺术家存在性验证通过")
            case (None, message) => IO.raiseError(new IllegalStateException(s"艺术家不存在: $message"))
          }
        case "Band" =>
          GetBandByID(userID, userToken, creatorID).send.flatMap {
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
      
      // 获取创作者的所有作品
      songs <- getCreatorSongs()
      _ <- logInfo(s"获取到创作者作品 ${songs.length} 首")
      
      // 如果没有作品，返回空倾向
      tendency <- if (songs.isEmpty) {
        logInfo("创作者暂无作品，返回空倾向")
        IO.pure(Profile(List.empty, norm = true))
      } else {
        // 统计各曲风的作品分布
        for {
          genreDistribution <- calculateGenreDistribution(songs)
          _ <- logInfo(s"计算出曲风分布: ${genreDistribution}")
          
          // 归一化处理
          normalizedDistribution = StatisticsUtils.normalizeVector(genreDistribution)
          profile = Profile(normalizedDistribution, norm = true)
        } yield profile
      }
      
    } yield tendency
  }

  /**
   * 获取创作者的所有作品
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
   * 使用 GetSongProfile API 计算曲风分布
   */
  private def calculateGenreDistribution(songs: List[String])(using PlanContext): IO[List[(String, Double)]] = {
    // 3. 为每首歌调用 GetSongProfile API 获取其曲风 profile
    songs.traverse { songId =>
      GetSongProfile(userID, userToken, songId).send.map {
        case (Some(profile), _) => profile.vector // 成功获取，返回 (GenreID, 1.0) 列表
        case (None, message) =>
          // 获取失败，记录警告并返回空列表，避免中断整个流程
          logger.warn(s"TID=${planContext.traceID.id} -- 获取歌曲 ${songId} 的Profile失败: $message. 将跳过此歌曲.")
          List.empty[(String, Double)]
      }
    }.map { listOfVectors =>
      // listOfVectors 是一个 List[List[(String, Double)]]
      // 将所有歌曲的曲风向量展平到一个列表中
      val allGenrePairs = listOfVectors.flatten
      
      // 按曲风ID分组，并对分数求和（相当于统计每种曲风的歌曲数量）
      val genreCounts = allGenrePairs
        .groupBy(_._1) // 按 GenreID 分组
        .view.mapValues(pairs => pairs.map(_._2).sum) // 对每个组内的分数求和
        .toList
      
      genreCounts
    }
  }

  private def logInfo(message: String): IO[Unit] = 
    IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))
    
  private def logError(message: String, cause: Throwable): IO[Unit] = 
    IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}