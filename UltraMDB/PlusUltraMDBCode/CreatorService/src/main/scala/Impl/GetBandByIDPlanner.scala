// ===== src/main/scala/Impl/GetBandByIDPlanner.scala =====

package Impl

import APIs.OrganizeService.validateUserMapping
import Common.API.{PlanContext, Planner}
import Objects.CreatorService.Band
import Utils.SearchUtil // <-- 新增: 导入 SearchUtil
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory

case class GetBandByIDPlanner(
  userID: String,
  userToken: String,
  bandID: String,
  override val planContext: PlanContext
) extends Planner[(Option[Band], String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Option[Band], String)] = {
    val logic: IO[(Option[Band], String)] = for {
      // 1. 用户认证 (保留)
      _ <- validateUser()

      // 2. 从 SearchUtil 获取乐队信息 (重构)
      bandOpt <- SearchUtil.fetchBandFromDB(bandID)

    } yield {
       // 3. 格式化成功响应 (根据 Option 的结果)
      bandOpt match {
        case Some(band) => (Some(band), "查询乐队成功")
        case None       => (None, s"未找到ID为 ${bandID} 的乐队")
      }
    }

    // 统一错误处理
    logic.handleErrorWith { error =>
      logError(s"查询乐队 ${bandID} 的操作失败", error) >>
        IO.pure((None, s"查询乐队失败: ${error.getMessage}"))
    }
  }

  /** 验证用户身份 */
  private def validateUser()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证用户身份: userID=${userID}")
    validateUserMapping(userID, userToken).send.flatMap {
      case (true, _) => logInfo("用户验证通过。")
      case (false, errorMsg) => IO.raiseError(new Exception(s"用户认证失败: $errorMsg"))
    }
  }

  // fetchAndProcessBand 方法已被移除，逻辑移至 SearchUtil

  /** 记录日志 */
  private def logInfo(message: String): IO[Unit] = IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))
  private def logError(message: String, cause: Throwable): IO[Unit] = IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}