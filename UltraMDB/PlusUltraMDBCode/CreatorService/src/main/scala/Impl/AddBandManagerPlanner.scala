package Impl


import APIs.OrganizeService.ValidateAdminMapping
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import cats.effect.IO
import org.slf4j.LoggerFactory
import io.circe.Json
import io.circe.parser._
import io.circe.syntax._
import io.circe.generic.auto._
import org.joda.time.DateTime
import cats.implicits.*
import Common.Serialize.CustomColumnTypes.{decodeDateTime, encodeDateTime}
import Objects.OrganizeService.User
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
import Objects.OrganizeService.User
import io.circe._
import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

case class AddBandManagerMessage(
  adminID: String,
  adminToken: String,
  userID: String,
  bandID: String
) extends API[String](serviceCode = "AddBandManager")

case class AddBandManagerPlanner(
  adminID: String,
  adminToken: String,
  userID: String,
  bandID: String,
  override val planContext: PlanContext
) extends Planner[String] {

  val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  override def plan(using PlanContext): IO[String] = {
    for {
      // Step 1: Validate admin credentials
      _ <- IO(logger.info(s"[Step 1] 验证管理员 ${adminID} 的Token是否有效"))
      isAdminValid <- ValidateAdminMapping(adminID, adminToken).send
      _ <- if (!isAdminValid) IO.raiseError(new IllegalArgumentException("管理员认证失败")) else IO.unit

      // Step 2: Check if userID exists
      _ <- IO(logger.info(s"[Step 2] 检查用户 ${userID} 是否存在"))
      userExists <- checkUserExists(userID)
      _ <- if (!userExists) IO.raiseError(new IllegalArgumentException("当前用户不存在")) else IO.unit

      // Step 3: Check if bandID exists
      _ <- IO(logger.info(s"[Step 3] 检查乐队 ${bandID} 是否存在"))
      bandExists <- checkBandExists(bandID)
      _ <- if (!bandExists) IO.raiseError(new IllegalArgumentException("乐队ID不存在")) else IO.unit

      // Step 4: Check if userID is already in managed_by
      _ <- IO(logger.info(s"[Step 4] 检查用户 ${userID} 是否已绑定为乐队 ${bandID} 的管理者"))
      isAlreadyManager <- checkUserIsManager(bandID, userID)
      _ <- if (isAlreadyManager) IO.raiseError(new IllegalArgumentException("当前用户已与该乐队绑定")) else IO.unit

      // Step 5: Add userID to managed_by
      _ <- IO(logger.info(s"[Step 5] 将用户 ${userID} 添加到乐队 ${bandID} 管理者列表中"))
      _ <- addUserToManagerList(bandID, userID)

      // Step 6: Return success message
      result = "更新成功"
      _ <- IO(logger.info(s"[Step 6] ${result}"))
    } yield result
  }

  // Function to check if the user exists in the database
  private def checkUserExists(userID: String)(using PlanContext): IO[Boolean] = {
    val sql = s"SELECT COUNT(*) FROM ${schemaName}.user WHERE user_id = ?;"
    for {
      isUserValid <- readDBInt(sql, List(SqlParameter("String", userID))).map(_ > 0)
      _ <- IO(logger.info(s"检查结果: 用户 ${userID} 是否存在 = ${isUserValid}"))
    } yield isUserValid
  }

  // Function to check if the band exists in the database
  private def checkBandExists(bandID: String)(using PlanContext): IO[Boolean] = {
    val sql = s"SELECT COUNT(*) FROM ${schemaName}.band_table WHERE band_id = ?;"
    for {
      isBandValid <- readDBInt(sql, List(SqlParameter("String", bandID))).map(_ > 0)
      _ <- IO(logger.info(s"检查结果: 乐队 ${bandID} 是否存在 = ${isBandValid}"))
    } yield isBandValid
  }

  // Function to check if the user is already in the managed_by list of the band
  private def checkUserIsManager(bandID: String, userID: String)(using PlanContext): IO[Boolean] = {
    val sql = s"SELECT managed_by FROM ${schemaName}.band_table WHERE band_id = ?;"
    for {
      managedByJsonOpt <- readDBJsonOptional(sql, List(SqlParameter("String", bandID)))
      isUserManager <- IO {
        managedByJsonOpt match {
          case Some(json) =>
            val managedByList = decodeField[List[String]](json, "managed_by")
            managedByList.contains(userID)
          case None => false
        }
      }
      _ <- IO(logger.info(s"检查用户 ${userID} 是否已经是乐队 ${bandID} 的管理者: ${isUserManager}"))
    } yield isUserManager
  }

  // Function to add userID to the managed_by list of the band
  private def addUserToManagerList(bandID: String, userID: String)(using PlanContext): IO[Unit] = {
    val selectSql = s"SELECT managed_by FROM ${schemaName}.band_table WHERE band_id = ?;"
    val updateSql = s"UPDATE ${schemaName}.band_table SET managed_by = ? WHERE band_id = ?;"

    for {
      // Get the current managed_by list
      managedByJsonOpt <- readDBJsonOptional(selectSql, List(SqlParameter("String", bandID)))
      currentManagedBy <- IO {
        managedByJsonOpt match {
          case Some(json) => decodeField[List[String]](json, "managed_by")
          case None       => List.empty[String]
        }
      }

      // Log current managed_by list
      _ <- IO(logger.info(s"当前乐队 ${bandID} 管理者列表: ${currentManagedBy}"))

      // Add the userID to the managed_by list
      updatedManagedBy = (currentManagedBy :+ userID).distinct
      _ <- IO(logger.info(s"更新后的管理者列表为: ${updatedManagedBy}"))
      managedByParam = SqlParameter("String", updatedManagedBy.asJson.noSpaces)

      // Update the managed_by list in the database
      _ <- writeDB(updateSql, List(managedByParam, SqlParameter("String", bandID)))
    } yield ()
  }
}