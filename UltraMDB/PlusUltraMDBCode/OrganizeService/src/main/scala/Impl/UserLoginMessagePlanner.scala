package Impl

import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import Utils.CryptoUtils
import Common.Serialize.CustomColumnTypes.encodeDateTime

/**
 * Planner for UserLoginMessage: 处理用户登录请求.
 *
 * @param userName    用户账户名
 * @param password    用户传入的明文密码
 * @param planContext 隐式执行上下文
 */
// 已修正: Planner 的返回值类型
case class UserLoginMessagePlanner(
  userName: String,
  password: String,
  override val planContext: PlanContext
) extends Planner[(Option[(String, String)], String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  private case class UserAuthInfo(userID: String, password: String)

  override def plan(using planContext: PlanContext): IO[(Option[(String, String)], String)] = {
    // 已修正: logic 现在返回一个包含 userID 和 token 的元组
    val logic: IO[(String, String)] = for {
      _ <- logInfo(s"开始为用户 ${userName} 处理登录请求")
      user <- findUser(userName)
      _ <- verifyUserPassword(password, user.password)
      // generateAndUpdateToken 现在需要返回生成的 token
      newToken <- generateAndUpdateToken(user.userID)
      // 将 userID 和 newToken 组合成元组返回
    } yield (user.userID, newToken)

    // 已修正: .map 的构造方式
    logic.map { case (userID, token) =>
      (Some((userID, token)), "登录成功")
    }.handleErrorWith { error =>
      logError(s"用户 ${userName} 登录失败", error) >>
        IO.pure((None, error.getMessage))
    }
  }

  private def findUser(account: String)(using PlanContext): IO[UserAuthInfo] = {
    logInfo(s"正在数据库中查找用户: ${account}") >>
      getUserByUserName(account).flatMap {
        case Some(user) => IO.pure(user)
        case None       => IO.raiseError(new Exception("用户名或密码错误"))
      }
  }

  private def verifyUserPassword(plainTextPassword: String, storedHash: String)(using PlanContext): IO[Unit] = {
    logInfo("正在验证密码") >> {
      if (CryptoUtils.verifyPassword(plainTextPassword, storedHash)) {
        logInfo("密码验证通过")
      } else {
        IO.raiseError(new Exception("用户名或密码错误"))
      }
    }
  }

  // generateAndUpdateToken 保持不变，它只负责生成和更新，并返回新token
  private def generateAndUpdateToken(userID: String)(using PlanContext): IO[String] = {
    for {
      _ <- logInfo(s"为用户 ${userID} 生成新Token")
      newToken <- IO(java.util.UUID.randomUUID().toString)
      _ <- logInfo(s"正在为用户 ${userID} 更新Token信息")
      _ <- updateTokenInfo(userID, newToken)
    } yield newToken
  }

  private def getUserByUserName(account: String)(using PlanContext): IO[Option[UserAuthInfo]] = {
    val query = s"SELECT user_id, password FROM ${schemaName}.user_table WHERE account = ?"
    readDBRows(query, List(SqlParameter("String", account))).flatMap {
      case row :: Nil =>
        IO.fromEither(row.as[UserAuthInfo])
          .map(Option.apply)
          .handleErrorWith(err => IO.raiseError(new Exception(s"解码UserAuthInfo失败: ${err.getMessage}")))
      case Nil => IO.pure(None)
      case _   => IO.raiseError(new Exception(s"数据库中存在多个同名账户: ${account}"))
    }
  }

  private def updateTokenInfo(userID: String, token: String)(using PlanContext): IO[Unit] = {
    val validUntil = DateTime.now().plusHours(24)
    val query = s"UPDATE ${schemaName}.user_table SET token = ?, token_valid_until = ? WHERE user_id = ?"
    writeDB(
      query,
      List(
        SqlParameter("String", token),
        SqlParameter("DateTime", validUntil.getMillis.toString),
        SqlParameter("String", userID)
      )
    ).void
  }

  private def logInfo(message: String): IO[Unit] = IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))
  private def logError(message: String, cause: Throwable): IO[Unit] = IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}