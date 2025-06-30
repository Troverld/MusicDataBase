package Impl

import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory
import Utils.CryptoUtils

/**
 * Planner for UserRegisterMessage: 处理新用户注册请求.
 *
 * @param userName    用户希望注册的账户名
 * @param password    用户设置的明文密码
 * @param planContext 隐式执行上下文
 */
case class UserRegisterMessagePlanner(
                                       userName: String,
                                       password: String,
                                       override val planContext: PlanContext
                                     ) extends Planner[(Option[String], String)] { // 1. 遵循API约定，修改返回类型

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Option[String], String)] = {
    // 2. 使用单一for-comprehension和统一错误处理
    val logic: IO[String] = for {
      _ <- logInfo(s"开始为新用户 ${userName} 处理注册请求")

      // 步骤 1: 验证用户名是否可用
      _ <- checkUsernameAvailability()

      // 步骤 2: 验证密码是否符合安全规则
      _ <- validatePasswordRules()

      // 步骤 3: 在数据库中创建新用户并获取其ID
      newUserID <- createNewUserInDB()

    } yield newUserID

    logic.map { userID =>
      (Some(userID), "注册成功")
    }.handleErrorWith { error =>
      logError(s"用户 ${userName} 注册失败", error) >>
        IO.pure((None, error.getMessage))
    }
  }

  /**
   * 步骤1的实现：检查用户名是否已存在.
   * 如果用户名已被占用，则返回一个失败的IO.
   */
  private def checkUsernameAvailability()(using PlanContext): IO[Unit] = {
    logInfo(s"正在检查用户名 ${userName} 是否可用") >> {
      val sql = s"SELECT COUNT(1) FROM ${schemaName}.user_table WHERE account = ?"
      readDBInt(sql, List(SqlParameter("String", userName))).flatMap { count =>
        if (count > 0) {
          IO.raiseError(new IllegalStateException("用户名已存在"))
        } else {
          logInfo("用户名可用")
        }
      }
    }
  }

  /**
   * 步骤2的实现：验证密码策略.
   */
  private def validatePasswordRules()(using PlanContext): IO[Unit] = {
    logInfo("正在验证密码复杂度") >> {
      val isLengthValid = password.length >= 8
      val hasLettersAndDigits = password.exists(_.isLetter) && password.exists(_.isDigit)

      if (isLengthValid && hasLettersAndDigits) {
        logInfo("密码复杂度验证通过")
      } else {
        IO.raiseError(new IllegalArgumentException("密码必须至少8位且同时包含字母和数字"))
      }
    }
  }

  /**
   * 步骤3的实现：生成用户ID，加密密码，并将新用户信息存入数据库.
   */
  private def createNewUserInDB()(using PlanContext): IO[String] = {
    for {
      userID <- IO(java.util.UUID.randomUUID().toString)
      _ <- logInfo(s"已为新用户生成ID: ${userID}")

      // 直接调用共享的加密方法
      encryptedPassword <- IO(CryptoUtils.encryptPassword(password))
      _ <- logInfo("密码已加密，准备写入数据库")

      sql = s"INSERT INTO ${schemaName}.user_table (user_id, account, password) VALUES (?, ?, ?)"
      _ <- writeDB(
        sql,
        List(
          SqlParameter("String", userID),
          SqlParameter("String", userName),
          SqlParameter("String", encryptedPassword)
        )
      )
    } yield userID
  }


  private def logInfo(message: String): IO[Unit] = IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))
  private def logError(message: String, cause: Throwable): IO[Unit] = IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}