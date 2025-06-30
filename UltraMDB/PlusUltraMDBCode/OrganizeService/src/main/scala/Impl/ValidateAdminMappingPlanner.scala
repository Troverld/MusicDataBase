package Impl

import APIs.OrganizeService.ValidateUserMapping // 复用已有的API
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory

/**
 * Planner for validAdminMapping: 验证用户是否为有效的管理员.
 *
 * @param adminID     需要验证的管理员用户ID
 * @param adminToken  该用户的认证令牌
 * @param planContext 隐式执行上下文
 */
case class ValidateAdminMappingPlanner(
                                        adminID: String,
                                        adminToken: String,
                                        override val planContext: PlanContext
                                      ) extends Planner[(Boolean, String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Boolean, String)] = {
    // 核心验证逻辑，成功则返回 Unit，失败则抛出带有信息的异常
    val validationLogic: IO[Unit] = for {
      _ <- logInfo(s"开始验证用户 ${adminID} 的管理员身份")

      // 步骤 1: 验证用户身份和令牌的有效性
      _ <- validateAsUser()

      // 步骤 2: 验证用户是否拥有管理员权限
      _ <- checkAdminPrivilege()

    } yield ()

    // 将验证逻辑的结果映射到 API 定义的 (Boolean, String) 返回类型
    validationLogic.map { _ =>
      (true, "管理员验证成功")
    }.handleErrorWith { error =>
      logError(s"用户 ${adminID} 管理员身份验证失败", error) >>
        IO.pure((false, error.getMessage))
    }
  }

  /**
   * 步骤1的实现：首先将该管理员作为一个普通用户进行验证.
   * 我们直接调用 ValidateUserMapping API 来复用逻辑。
   */
  private def validateAsUser()(using PlanContext): IO[Unit] = {
    logInfo(s"正在将用户 ${adminID} 作为普通用户进行验证")
    ValidateUserMapping(adminID, adminToken).send.flatMap {
      case (true, _) =>
        logInfo("用户身份及令牌有效")
      case (false, message) =>
        IO.raiseError(new SecurityException(s"用户身份验证失败: $message"))
    }
  }

  /**
   * 步骤2的实现：在 AdminTable 中检查该用户ID是否存在.
   */
  private def checkAdminPrivilege()(using PlanContext): IO[Unit] = {
    logInfo(s"正在 AdminTable 中查询用户 ${adminID} 的权限")
    val sql = s"SELECT COUNT(1) FROM ${schemaName}.admin_table WHERE admin_id = ?"
    readDBInt(sql, List(SqlParameter("String", adminID))).flatMap {
      case 1 =>
        logInfo("用户拥有管理员权限")
        IO.unit
      case 0 =>
        logInfo("用户不在管理员列表中")
        IO.raiseError(new SecurityException("权限不足"))
      case _ =>
        // 这是一个数据异常情况，理论上不应该发生
        IO.raiseError(new IllegalStateException(s"数据库中存在重复的管理员ID: ${adminID}"))
    }
  }

  private def logInfo(message: String): IO[Unit] = IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))
  private def logError(message: String, cause: Throwable): IO[Unit] = IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}
