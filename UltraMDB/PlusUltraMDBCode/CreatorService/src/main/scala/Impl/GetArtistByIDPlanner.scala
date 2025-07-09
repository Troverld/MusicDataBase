// ===== src/main/scala/Impl/GetArtistByIDPlanner.scala =====

package Impl

import APIs.OrganizeService.validateUserMapping
import Common.API.{PlanContext, Planner}
import Objects.CreatorService.Artist
import Utils.SearchUtil // <-- 新增: 导入 SearchUtil
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory

case class GetArtistByIDPlanner(
  userID: String,
  userToken: String,
  artistID: String,
  override val planContext: PlanContext
) extends Planner[(Option[Artist], String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Option[Artist], String)] = {
    val logic: IO[(Option[Artist], String)] = for {
      // 1. 用户认证 (保留)
      _ <- validateUser()

      // 2. 从 SearchUtil 获取艺术家信息 (重构)
      artistOpt <- SearchUtil.fetchArtistFromDB(artistID)

    } yield {
      // 3. 格式化成功响应
      artistOpt match {
        case Some(artist) => (Some(artist), "查询艺术家成功")
        case None         => (None, s"未找到ID为 ${artistID} 的艺术家")
      }
    }

    // 统一错误处理
    logic.handleErrorWith { error =>
      logError(s"查询艺术家 ${artistID} 的操作失败", error) >>
        IO.pure((None, s"查询艺术家失败: ${error.getMessage}"))
    }
  }

  /**
   * 验证用户身份
   */
  private def validateUser()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证用户身份: userID=${userID}")
    validateUserMapping(userID, userToken).send.flatMap {
      case (true, _) => logInfo("用户验证通过。")
      case (false, message) => IO.raiseError(new Exception(s"用户认证失败: $message"))
    }
  }

  // fetchArtist 方法已被移除，逻辑移至 SearchUtil

  private def logInfo(message: String): IO[Unit] = IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))
  private def logError(message: String, cause: Throwable): IO[Unit] = IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}