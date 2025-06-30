package Impl

import APIs.OrganizeService.ValidateUserMapping
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

/**
 * Planner for UserLogoutMessage: 处理用户登出请求.
 *
 * @param userID      发起登出请求的用户ID
 * @param userToken   用户的认证令牌
 * @param planContext 隐式执行上下文
 */
case class UserLogoutMessagePlanner(
                                     userID: String,
                                     userToken: String,
                                     override val planContext: PlanContext
                                   ) extends Planner[(Boolean, String)] { // 1. 遵循API约定，修改返回类型

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Boolean, String)] = {
    // 2 & 3. 使用单一for-comprehension和统一错误处理，简化逻辑流
    val logic: IO[Unit] = for {
      _ <- logInfo(s"开始为用户 ${userID} 处理登出请求")

      // 步骤 1: 验证用户令牌的有效性
      _ <- ValidateUser()

      // 步骤 2: 在数据库中将Token标记为无效
      _ <- invalidateTokenInDB()

    } yield ()

    logic.map { _ =>
      (true, "登出成功")
    }.handleErrorWith { error =>
      logError(s"用户 ${userID} 登出失败", error) >>
        IO.pure((false, error.getMessage))
    }
  }

  /**
   * 步骤1的实现：验证用户.
   * 如果验证失败，则返回一个携带具体错误信息的失败IO.
   */
  private def ValidateUser()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证用户 ${userID} 的令牌") >>
      ValidateUserMapping(userID, userToken).send.flatMap {
        case (true, _) =>
          logInfo("用户令牌验证通过")
        case (false, message) =>
          IO.raiseError(new Exception(s"用户验证失败: $message"))
      }
  }

  /**
   * 步骤2的实现：更新数据库，使Token失效.
   * 这通常通过更新一个时间戳字段来实现。
   */
  private def invalidateTokenInDB()(using PlanContext): IO[Unit] = {
    val currentTime = DateTime.now()
    logInfo(s"正在将用户 ${userID} 的令牌失效时间更新为: ${currentTime}")

    val sql =
      s"""
        UPDATE ${schemaName}.user_table
        SET invalid_time = ?
        WHERE user_id = ? AND token = ?; 
      """
    // 注意: 添加了 `AND token = ?` 条件，确保我们只让当前使用的token失效，
    // 这是一个更安全的实践，防止并发登录场景下的问题。
    writeDB(
      sql,
      List(
        SqlParameter("DateTime", currentTime.getMillis.toString),
        SqlParameter("String", userID),
        SqlParameter("String", userToken)
      )
    ).void // 5. 使用.void简化数据库操作，让错误自然冒泡
  }

  private def logInfo(message: String): IO[Unit] = IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))
  private def logError(message: String, cause: Throwable): IO[Unit] = IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}