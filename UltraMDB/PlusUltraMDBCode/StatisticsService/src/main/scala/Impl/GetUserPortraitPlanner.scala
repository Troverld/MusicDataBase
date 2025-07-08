package Impl

import Common.API.{PlanContext, Planner}
import APIs.OrganizeService.validateUserMapping
import Objects.StatisticsService.Profile
import Utils.GetUserPortraitUtils // 导入新的业务逻辑层
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory

/**
 * Planner for GetUserPortrait: 获取用户的音乐偏好画像。
 * 此 Planner 作为 API 的入口，负责验证和协调，核心业务逻辑已移至 PortraitUtils。
 *
 * @param userID      目标用户的ID
 * @param userToken   用户认证令牌
 * @param planContext 执行上下文
 */
case class GetUserPortraitPlanner(
  userID: String,
  userToken: String,
  override val planContext: PlanContext
) extends Planner[(Option[Profile], String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Option[Profile], String)] = {
    val logic: IO[Profile] = for {
      _ <- logInfo(s"开始处理获取用户 ${userID} 画像的请求")

      // 步骤 1: 执行 API 入口层的验证工作
      _ <- validateUser()

      // 步骤 2: 调用集中的业务逻辑服务来执行核心任务
      _ <- logInfo(s"验证通过，正在调用 PortraitUtils.generateUserProfile for user ${userID}")
      // 将 userToken 传递给业务逻辑层，因为它内部需要调用需要认证的API
      profile <- GetUserPortraitUtils.generateUserProfile(userID, userToken)
      _ <- logInfo(s"画像计算完成，包含 ${profile.vector.length} 个维度")

    } yield profile

    // 步骤 3: 格式化最终的成功或失败响应
    logic.map { profile =>
      (Some(profile), "获取用户画像成功")
    }.handleErrorWith { error =>
      logError(s"获取用户 ${userID} 画像失败", error) >>
        IO.pure((None, error.getMessage))
    }
  }

  /**
   * 验证发起请求的用户身份是否有效。
   * 这是 Planner 的职责，确保只有授权用户可以访问。
   */
  private def validateUser()(using PlanContext): IO[Unit] = {
    logInfo("正在验证用户身份") >>
      validateUserMapping(userID, userToken).send.flatMap {
        case (true, _) => logInfo("用户身份验证通过")
        case (false, message) => IO.raiseError(new IllegalArgumentException(s"用户身份验证失败: $message"))
      }
  }

  // 日志记录的辅助方法
  private def logInfo(message: String): IO[Unit] =
    IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))

  private def logError(message: String, cause: Throwable): IO[Unit] =
    IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}