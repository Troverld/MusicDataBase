package Impl


import APIs.OrganizeService.validateAdminMapping
import APIs.CreatorService.AddArtistManager
import Objects.OrganizeService.RequestStatus
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import org.slf4j.LoggerFactory
import cats.effect.IO
import io.circe.Json
import io.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import org.joda.time.DateTime
import cats.implicits.*
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
import Objects.OrganizeService.RequestStatus
import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

case class ApproveArtistAuthRequestMessagePlanner(
  adminID: String,
  adminToken: String,
  requestID: String,
  approve: Boolean,
  override val planContext: PlanContext
) extends Planner[String] {

  private val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  // Main logic entry
  override def plan(using planContext: PlanContext): IO[String] = {
    for {
      // Step 1: Validate admin credentials and permissions
      _ <- logInfo(s"开始处理管理员 ${adminID} 对认证请求 ${requestID} 的审批操作")
      isAdminValid <- validateAdminMapping(adminID, adminToken).send
      _ <- validateAdmin(isAdminValid)

      // Step 2: Retrieve request record from the database
      requestRecord <- getArtistAuthRequest(requestID)
      _ <- verifyRequestStatus(requestRecord, RequestStatus.Pending)

      // Step 3: Process approval or rejection
      result <- if (approve) {
        approveRequest(requestRecord)
      } else {
        rejectRequest(requestID)
      }

    } yield result
  }

  // Step 1 - Validate admin
  private def validateAdmin(isAdminValid: Boolean)(using PlanContext): IO[Unit] = {
    if (isAdminValid) {
      logInfo(s"管理员 ${adminID} 验证通过")
    } else {
      IO.raiseError(new Exception("管理员身份验证失败"))
    }
  }

  // Step 2.1 - Query ArtistAuthRequestTable for the record
  private def getArtistAuthRequest(requestID: String)(using PlanContext): IO[Json] = {
    val sql =
      s"""
         SELECT * FROM ${schemaName}.artist_auth_request_table
         WHERE request_id = ?;
       """
    logInfo(s"查询 ArtistAuthRequestTable 获取 requestID 为 ${requestID} 的申请记录") >>
    readDBJson(sql, List(SqlParameter("String", requestID)))
  }

  // Step 2.2 - Verify status
  private def verifyRequestStatus(requestRecord: Json, expectedStatus: RequestStatus)(using PlanContext): IO[Unit] = {
    val status = decodeField[String](requestRecord, "status")
    if (status == expectedStatus.toString) {
      logInfo(s"申请记录 ${requestID} 的状态为 ${expectedStatus}，可以进行审核")
    } else {
      IO.raiseError(new Exception(s"申请记录 ${requestID} 的状态为 ${status}，无法进行审核"))
    }
  }

  // Step 3.1 - Approve the request
  private def approveRequest(requestRecord: Json)(using PlanContext): IO[String] = {
    for {
      artistID <- IO(decodeField[String](requestRecord, "artist_id"))
      userID <- IO(decodeField[String](requestRecord, "user_id"))

      _ <- logInfo(s"将请求 ${requestID} 的状态更新为 Approved")
      _ <- updateRequestStatus(requestID, RequestStatus.Approved)

      _ <- logInfo(s"调用 AddArtistManager，为艺术家 ${artistID} 绑定管理员 ${userID}")
      _ <- AddArtistManager(adminID, adminToken, userID, artistID).send

    } yield "操作已成功完成"
  }

  // Step 3.2 - Reject the request
  private def rejectRequest(requestID: String)(using PlanContext): IO[String] = {
    logInfo(s"将请求 ${requestID} 的状态更新为 Rejected") >>
    updateRequestStatus(requestID, RequestStatus.Rejected).map(_ => "操作已成功完成")
  }

  // Helper - Update the request status in the database
  private def updateRequestStatus(requestID: String, status: RequestStatus)(using PlanContext): IO[Unit] = {
    val sql =
      s"""
         UPDATE ${schemaName}.artist_auth_request_table
         SET status = ?
         WHERE request_id = ?;
       """
    writeDB(
      sql,
      List(
        SqlParameter("String", status.toString),
        SqlParameter("String", requestID)
      )
    ).void
  }

  // Helper - Log information
  private def logInfo(message: String): IO[Unit] = {
    IO(logger.info(message))
  }
}