package Impl

import Common.API.{PlanContext, Planner}
import APIs.OrganizeService.validateUserMapping
import APIs.MusicService.GetSongByID
import Utils.SearchUtils // 导入 SearchUtils
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory

/**
 * Planner for LogPlayback: 记录用户播放歌曲的行为。
 * 此 Planner 作为 API 的入口，负责验证和协调，核心的数据库写入操作已移至 SearchUtils。
 *
 * @param userID      播放用户的ID
 * @param userToken   用户认证令牌
 * @param songID      播放的歌曲ID
 * @param planContext 执行上下文
 */
case class LogPlaybackPlanner(
                               userID: String,
                               userToken: String,
                               songID: String,
                               override val planContext: PlanContext
                             ) extends Planner[(Boolean, String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Boolean, String)] = {
    val logic: IO[Unit] = for {
      _ <- logInfo(s"开始处理用户 ${userID} 播放歌曲 ${songID} 的记录请求")

      // 步骤 1: 执行 API 入口层的验证工作
      _ <- validateUser()
      _ <- validateSong()

      // 步骤 2: 调用集中的数据访问服务来执行核心的写操作
      _ <- logInfo(s"验证通过，正在调用 SearchUtils.logPlayback")
      _ <- SearchUtils.logPlayback(userID, songID)
      _ <- logInfo("播放记录写入操作已委托给 SearchUtils")

    } yield ()

    // 步骤 3: 格式化最终的成功或失败响应
    logic.map { _ =>
      (true, "播放记录成功")
    }.handleErrorWith { error =>
      logError(s"用户 ${userID} 播放歌曲 ${songID} 记录失败", error) >>
        IO.pure((false, error.getMessage))
    }
  }

  /**
   * 验证发起请求的用户身份是否有效。
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
   */
  private def validateSong()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证歌曲 ${songID} 是否存在") >>
      GetSongByID(userID, userToken, songID).send.flatMap {
        case (Some(_), _) => logInfo("歌曲存在性验证通过")
        case (None, message) => IO.raiseError(new IllegalArgumentException(s"歌曲不存在: $message"))
      }
  }

  // 日志记录的辅助方法
  private def logInfo(message: String): IO[Unit] =
    IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))

  private def logError(message: String, cause: Throwable): IO[Unit] =
    IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}