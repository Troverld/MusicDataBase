// ===== src/main/scala/Impl/UnrateSongPlanner.scala (MODIFIED) =====

package Impl

import Common.API.{PlanContext, Planner}
import APIs.OrganizeService.validateUserMapping
import Utils.SearchUtils // 直接导入 SearchUtils
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory

/**
 * Planner for UnrateSong: 处理用户撤销歌曲评分的请求。
 *
 * @param userID      发起操作的用户ID
 * @param userToken   用户认证令牌
 * @param songID      要撤销评分的歌曲ID
 * @param planContext 执行上下文
 */
case class UnrateSongPlanner(
  userID: String,
  userToken: String,
  songID: String,
  override val planContext: PlanContext
) extends Planner[(Boolean, String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Boolean, String)] = {
    val logic: IO[Unit] = for {
      _ <- logInfo(s"开始处理用户 ${userID} 撤销对歌曲 ${songID} 评分的请求")

      // 步骤 1: 验证用户身份
      _ <- validateUser()

      // 步骤 2: 直接调用 SearchUtils 执行数据库操作
      _ <- logInfo("验证通过，正在直接调用 SearchUtils.deleteUserSongRating")
      _ <- SearchUtils.deleteUserSongRating(userID, songID)
      _ <- logInfo("撤销评分的数据库操作已完成")

    } yield ()

    // 步骤 3: 格式化最终响应
    logic.map { _ =>
      (true, "评分撤销成功")
    }.handleErrorWith { error =>
      logError(s"用户 ${userID} 撤销对歌曲 ${songID} 的评分失败", error) >>
        IO.pure((false, error.getMessage))
    }
  }

  private def validateUser()(using PlanContext): IO[Unit] = {
    logInfo("正在验证用户身份") >>
      validateUserMapping(userID, userToken).send.flatMap {
        case (true, _) => logInfo("用户身份验证通过")
        case (false, message) => IO.raiseError(new IllegalArgumentException(s"用户身份验证失败: $message"))
      }
  }

  private def logInfo(message: String): IO[Unit] =
    IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))

  private def logError(message: String, cause: Throwable): IO[Unit] =
    IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}