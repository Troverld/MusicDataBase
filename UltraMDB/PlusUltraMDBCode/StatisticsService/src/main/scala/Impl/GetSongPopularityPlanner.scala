package Impl

import Common.API.{PlanContext, Planner}
import APIs.OrganizeService.validateUserMapping
import APIs.MusicService.GetSongByID
import Utils.GetSongPopularityUtils // 导入新的业务逻辑层工具
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory

/**
 * Planner for GetSongPopularity: 获取歌曲的热度分数。
 * 此 Planner 作为 API 的入口，负责验证和协调，核心业务逻辑已移至 GetSongPopularityUtils。
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
    val logic: IO[Double] = for {
      _ <- logInfo(s"开始处理获取歌曲 ${songID} 热度的请求")

      // 步骤 1: 执行 API 入口层的验证工作
      _ <- validateUser()
      
      _ <- validateSong()

      // 步骤 2: 调用集中的业务逻辑服务来执行核心任务
      _ <- logInfo("验证通过，正在调用 ProcessUtils.calculatePopularity")
      popularity <- GetSongPopularityUtils.calculatePopularity(songID)
      _ <- logInfo(s"计算完成，热度为: $popularity")

    } yield popularity

    // 步骤 3: 格式化最终的成功或失败响应
    logic.map { popularity =>
      (Some(popularity), "获取歌曲热度成功")
    }.handleErrorWith { error =>
      logError(s"获取歌曲 ${songID} 热度失败", error) >>
        IO.pure((None, error.getMessage))
    }
  }

  /**
   * 验证发起请求的用户身份是否有效。
   * 这是 Planner 的职责之一，确保只有授权用户可以访问。
   */
  private def validateUser()(using PlanContext): IO[Unit] = {
    logInfo("正在验证用户身份") >>
      validateUserMapping(userID, userToken).send.flatMap {
        case (true, _) => logInfo("用户身份验证通过")
        case (false, message) => IO.raiseError(new IllegalArgumentException(s"用户身份验证失败: $message"))
      }
  }

  /**
   * 验证目标歌曲是否存在。
   * 这是 Planner 的职责之一，用于快速失败，避免对无效ID执行昂贵操作。
   */
  private def validateSong()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证歌曲 ${songID} 是否存在") >>
      GetSongByID(userID, userToken, songID).send.flatMap {
        case (Some(_), _) => logInfo("歌曲存在性验证通过")
        case (None, message) => IO.raiseError(new IllegalStateException(s"目标歌曲不存在: $message"))
      }
  }

  // 日志记录的辅助方法
  private def logInfo(message: String): IO[Unit] = 
    IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))
    
  private def logError(message: String, cause: Throwable): IO[Unit] = 
    IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}