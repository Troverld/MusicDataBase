package Impl


import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import cats.effect.IO
import org.slf4j.LoggerFactory
import org.joda.time.DateTime
import io.circe.generic.auto.*
import io.circe.syntax.*
import io.circe.*
import cats.implicits._
import Common.Serialize.CustomColumnTypes.{decodeDateTime, encodeDateTime}
import io.circe._
import io.circe.syntax._
import io.circe.generic.auto._
import org.joda.time.DateTime
import cats.implicits.*
import Common.DBAPI._
import Common.API.{PlanContext, Planner}
import cats.effect.IO
import Common.Object.SqlParameter
import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}
import Common.ServiceUtils.schemaName

import io.circe.generic.auto._
import io.circe.syntax._
import io.circe._
import cats.implicits.*
import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

case class UserRegisterMessagePlanner(
                                       userName: String,
                                       password: String,
                                       override val planContext: PlanContext
                                     ) extends Planner[String] {
  private val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  override def plan(using planContext: PlanContext): IO[String] = {
    for {
      _ <- IO(logger.info(s"开始执行用户注册操作，用户名为: ${userName}"))

      // Step 1: 验证用户名是否已存在
      isUserNameAvailable <- checkUserNameAvailability()
      _ <- IO(logger.info(s"用户名是否可用: ${isUserNameAvailable}"))
      _ <- if (!isUserNameAvailable)
        IO.raiseError(new IllegalStateException(s"用户名 ${userName} 已存在"))
      else IO.unit

      // Step 2: 验证密码是否符合规则
      _ <- IO(logger.info(s"验证密码是否符合安全规则"))
      _ <- validatePassword()

      // Step 3: 生成用户唯一ID
      userID <- generateUserID()

      // Step 4: 加密密码并存储用户信息
      _ <- IO(logger.info(s"加密密码并存储用户信息到数据库"))
      encryptedPassword <- IO(encryptPassword(password))
      _ <- storeUserInfo(userID, userName, encryptedPassword)

      // Step 5: 返回注册成功信息
      _ <- IO(logger.info(s"用户注册成功，用户名: ${userName}, 用户ID: ${userID}"))
    } yield "SUCCESS"
  }

  private def checkUserNameAvailability()(using PlanContext): IO[Boolean] = {
    val sql =
      s"""
         |SELECT COUNT(1)
         |FROM ${schemaName}.user_table
         |WHERE account = ?;
       """.stripMargin
    readDBInt(sql, List(SqlParameter("String", userName))).map(_ == 0)
  }

  private def validatePassword()(using PlanContext): IO[Unit] = {
    val isLengthValid = password.length >= 8
    val hasLetters = password.exists(_.isLetter)
    val hasDigits = password.exists(_.isDigit)

    if (isLengthValid && hasLetters && hasDigits)
      IO.unit
    else
      IO.raiseError(new IllegalArgumentException("密码必须至少8位且包含字母和数字"))
  }

  private def generateUserID()(using PlanContext): IO[String] = {
    IO {
      val userID = java.util.UUID.randomUUID().toString
      logger.info(s"生成的用户ID为: ${userID}")
      userID
    }
  }

  private def encryptPassword(password: String): String = {
    // 使用简单Hash作为占位符，生产代码中建议使用更安全的加密方式
    password.reverse.hashCode.toString
  }

  private def storeUserInfo(userID: String, userName: String, encryptedPassword: String)(using PlanContext): IO[Unit] = {
    val sql =
      s"""
         |INSERT INTO ${schemaName}.user_table (user_id, account, password, invalid_time)
         |VALUES (?, ?, ?, NULL);
       """.stripMargin
    writeDB(sql, List(
      SqlParameter("String", userID),
      SqlParameter("String", userName),
      SqlParameter("String", encryptedPassword)
    )).void
  }
}