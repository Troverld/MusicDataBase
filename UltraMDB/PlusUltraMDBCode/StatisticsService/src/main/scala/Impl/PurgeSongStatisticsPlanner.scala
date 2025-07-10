// ===== src/main/scala/Impl/PurgeSongStatisticsPlanner.scala (NEW FILE) =====

package Impl

import Common.API.{PlanContext, Planner}
import APIs.OrganizeService.validateAdminMapping
import Utils.PurgeSongStatisticsUtils
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory

/**
 * Planner for PurgeSongStatisticsMessage: 处理清理歌曲统计数据的管理请求。
 *
 * @param adminID     管理员ID
 * @param adminToken  管理员认证令牌
 * @param songID      要清理数据的歌曲ID
 * @param planContext 执行上下文
 */
case class PurgeSongStatisticsPlanner(
  adminID: String,
  adminToken: String,
  songID: String,
  override val planContext: PlanContext
) extends Planner[(Boolean, String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Boolean, String)] = {
    val logic: IO[Unit] = for {
      _ <- logInfo(s"开始处理清理歌曲 ${songID} 统计数据的管理请求，操作员: ${adminID}")

      // 步骤 1: 验证管理员身份
      _ <- validateAdmin()

      // *不可以验证歌曲是否存在！因为该方法在移除歌曲时被调用，所以无法知道其它 API 调用该方法时，是否已经将该歌曲移除！*

      // 步骤 2: 调用 Utils 层执行核心业务逻辑
      _ <- logInfo("管理员身份验证通过，正在调用 PurgeSongStatisticsUtils.purgeStatistics")
      _ <- PurgeSongStatisticsUtils.purgeStatistics(songID)
      _ <- logInfo(s"已委托 PurgeSongStatisticsUtils 完成对歌曲 ${songID} 的数据清理")

    } yield ()

    // 步骤 3: 格式化最终响应
    logic.map { _ =>
      (true, s"歌曲 ${songID} 的所有统计数据已成功清理")
    }.handleErrorWith { error =>
      logError(s"清理歌曲 ${songID} 的统计数据失败", error) >>
        IO.pure((false, error.getMessage))
    }
  }

  private def validateAdmin()(using PlanContext): IO[Unit] = {
    logInfo("正在验证管理员身份") >>
      validateAdminMapping(adminID, adminToken).send.flatMap {
        case (true, _) => logInfo("管理员身份验证通过")
        case (false, message) => IO.raiseError(new SecurityException(s"管理员身份验证失败: $message"))
      }
  }

  private def logInfo(message: String): IO[Unit] =
    IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))

  private def logError(message: String, cause: Throwable): IO[Unit] =
    IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}