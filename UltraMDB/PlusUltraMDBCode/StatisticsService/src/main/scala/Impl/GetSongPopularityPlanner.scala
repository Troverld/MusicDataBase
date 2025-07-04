package Impl

import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import APIs.OrganizeService.validateUserMapping
import APIs.MusicService.GetSongByID
import APIs.StatisticsService.GetAverageRating // 1. 导入新的API
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory

/**
 * Planner for GetSongPopularity: 获取歌曲的热度分数
 *
 * @param userID      请求用户的ID
 * @param userToken   用户认证令牌
 * @param songID      要查询热度的歌曲ID
 * @param planContext 执行上下文
 */
case class GetSongPopularityPlanner(
                                     userID: String,
                                     userToken: String,
                                     songID: String,
                                     override val planContext: PlanContext
                                   ) extends Planner[(Option[Double], String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Option[Double], String)] = {
    // 2. 简化核心逻辑，移除缓存判断
    val logic: IO[Double] = for {
      _ <- logInfo(s"开始获取歌曲 ${songID} 的热度分数")

      // 步骤1: 验证用户身份
      _ <- validateUser()

      // 步骤2: 验证歌曲存在性
      _ <- validateSong()

      // 步骤3: 实时计算热度
      popularity <- calculatePopularity()

    } yield popularity

    logic.map { popularity =>
      (Some(popularity), "获取歌曲热度成功")
    }.handleErrorWith { error =>
      logError(s"获取歌曲 ${songID} 热度失败", error) >>
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
   * 步骤2: 验证歌曲存在性
   */
  private def validateSong()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证歌曲 ${songID} 是否存在") >> {
      GetSongByID(userID, userToken, songID).send.flatMap { case (songOpt, message) =>
        songOpt match {
          case Some(_) =>
            logInfo("歌曲存在性验证通过")
          case None =>
            IO.raiseError(new IllegalStateException(s"歌曲不存在: $message"))
        }
      }
    }
  }

  /**
   * 步骤3: 实时计算热度
   */
  private def calculatePopularity()(using PlanContext): IO[Double] = {
    for {
      _ <- logInfo("开始实时计算歌曲热度")
      
      // 获取播放统计
      playCount <- getPlayCount()
      _ <- logInfo(s"歌曲播放次数: ${playCount}")
      
      // 3. 通过API获取评分统计，替换原有的DB查询
      _ <- logInfo(s"正在调用API获取歌曲 ${songID} 的平均评分")
      ratingResult <- GetAverageRating(userID, userToken, songID).send
      (avgRating, ratingCount) = ratingResult._1 // API返回 ((Double, Int), String)
      apiMessage = ratingResult._2
      _ <- logInfo(s"API调用完成, 消息: '$apiMessage'. 平均评分: ${avgRating}, 评分人数: ${ratingCount}")
      
      // 计算热度: 播放次数 * 0.7 + 平均评分 * 评分人数 * 0.3
      popularity = playCount * 0.7 + avgRating * ratingCount * 0.3
      _ <- logInfo(s"计算得出热度分数: ${popularity}")
      
    } yield popularity
  }

  /**
   * 获取歌曲播放次数
   */
  private def getPlayCount()(using PlanContext): IO[Int] = {
    val sql = s"SELECT COUNT(*) FROM ${schemaName}.playback_log WHERE song_id = ?"
    readDBInt(sql, List(SqlParameter("String", songID)))
  }

  private def logInfo(message: String): IO[Unit] = 
    IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))
    
  private def logError(message: String, cause: Throwable): IO[Unit] = 
    IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}