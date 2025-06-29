package Impl


import APIs.OrganizeService.{validateUserMapping, validateAdminMapping}
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import cats.effect.IO
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
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
import APIs.OrganizeService.validateUserMapping
import APIs.OrganizeService.validateAdminMapping
import io.circe._
import cats.implicits.*
import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}
import APIs.OrganizeService.validateAdminMapping

case class ValidBandOwnershipPlanner(
                                      userID: String,
                                      userToken: String,
                                      bandID: String,
                                      override val planContext: PlanContext
                                    ) extends Planner[Boolean] {

  val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  override def plan(using planContext: PlanContext): IO[Boolean] = {
    for {
      // Step 1: Validate the userToken and userID association
      _ <- IO(logger.info(s"[Step 1] 开始验证用户令牌与映射关系: userID=${userID}, userToken=${userToken}"))
      isUserValid <- validateUserMapping(userID, userToken).send
      _ <- IO(logger.info(s"[Step 1.1] 用户令牌验证结果: ${isUserValid}"))

      // Check if userToken is invalid
      _ <- if (!isUserValid) IO.raiseError(new IllegalStateException(s"[Step 1.2] 用户令牌验证失败，userID=${userID}, userToken=${userToken}"))
      else IO.unit

      // Step 2: Fetch the managedBy field from BandTable
      _ <- IO(logger.info(s"[Step 2] 查询BandTable中bandID=${bandID}对应的管理者信息"))
      managedByList <- getBandManagers(bandID)

      // Step 3: Check if userID exists in managedBy
      _ <- IO(logger.info(s"[Step 3] 验证用户是否为该乐队的管理者: bandID=${bandID}, userID=${userID}, managedBy=${managedByList}"))
      isOwner <- if (managedByList.contains(userID)) IO.pure(true)
      else checkIfAdmin(userID, userToken) // Step 4: If not manager, check if the user is an admin

    } yield isOwner
  }

  // Fetch the `managed_by` field from BandTable for the given bandID
  private def getBandManagers(bandID: String)(using PlanContext): IO[List[String]] = {
    val sql =
      s"SELECT managed_by FROM ${schemaName}.band_table WHERE band_id = ?"
    IO(logger.info(s"执行SQL查询: ${sql}, 参数: bandID=${bandID}")) >>
      readDBJsonOptional(sql, List(SqlParameter("String", bandID))).map {
        case Some(json) =>
          val managedBy = decodeField[Option[List[String]]](json, "managed_by")
          managedBy.getOrElse(List.empty[String]) // Return the list or empty if null
        case None =>
          val errorMsg = s"Band with bandID=${bandID} 不存在"
          logger.error(errorMsg)
          throw new IllegalStateException(errorMsg)
      }
  }

  // Check if the user is an administrator
  private def checkIfAdmin(adminID: String, adminToken: String)(using PlanContext): IO[Boolean] = {
    IO(logger.info(s"[Step 4] 用户非该乐队管理者，开始验证是否为管理员: adminID=${adminID}, adminToken=${adminToken}")) >>
      validateAdminMapping(adminID, adminToken).send.map { isAdminValid =>
        if (isAdminValid) {
          logger.info(s"[Step 4.1] 验证通过: 用户是管理员权限")
          true
        } else {
          logger.info(s"[Step 4.2] 验证失败: 用户既非管理员也非乐队管理者")
          false
        }
      }
  }
}