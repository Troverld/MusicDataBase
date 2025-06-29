package Impl


import APIs.OrganizeService.validateAdminMapping
import APIs.CreatorService.AddBandManager
import Objects.OrganizeService.RequestStatus
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import io.circe.Json
import cats.effect.IO
import org.slf4j.LoggerFactory
import io.circe.Decoder
import io.circe.syntax.*
import io.circe.generic.auto.*
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
import io.circe._
import io.circe.syntax._
import io.circe.generic.auto._
import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

case class ApproveBandAuthRequestMessagePlanner(
    adminID: String,
    adminToken: String,
    requestID: String,
    approve: Boolean,
    override val planContext: PlanContext
) extends Planner[String] {

  private val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  override def plan(using planContext: PlanContext): IO[String] = for {
    // Step 1: Validate admin ID and admin token
    _ <- IO(logger.info(s"Validating admin credentials for adminID: ${adminID}"))
    isValid <- validateAdminMapping(adminID, adminToken).send
    _ <- if (!isValid) IO.raiseError(new IllegalArgumentException(s"Invalid admin credentials for adminID: ${adminID}")) else IO.unit

    // Step 2: Fetch application record from BandAuthRequestTable
    _ <- IO(logger.info(s"Fetching application record for requestID: ${requestID}"))
    requestRecordJson <- fetchRequestRecordById()

    // Step 2.1: Validate that current request status is Pending
    currentStatus = decodeField[String](requestRecordJson, "status")
    _ <- if (RequestStatus.fromString(currentStatus) != RequestStatus.Pending)
           IO.raiseError(new IllegalStateException(s"Request with ID ${requestID} is not in Pending status"))
         else IO.unit

    // Step 3: Process approval or rejection
    _ <- if (approve) approveRequest(requestRecordJson) else rejectRequest()

    // Step 4: Return operation result
    resultMessage <- IO(logger.info(s"Completed processing requestID: ${requestID}. Result: Success")) >> IO("Success")
  } yield resultMessage

  private def fetchRequestRecordById()(using PlanContext): IO[Json] = {
    val sql = s"""SELECT * FROM ${schemaName}.band_auth_request_table WHERE request_id = ?"""
    readDBJson(sql, List(SqlParameter("String", requestID)))
  }

  private def approveRequest(requestRecordJson: Json)(using PlanContext): IO[Unit] = {
    val bandID = decodeField[String](requestRecordJson, "band_id")
    val userID = decodeField[String](requestRecordJson, "user_id")

    for {
      // Step 3.1.1: Update request status to Approved
      _ <- IO(logger.info(s"Approving request with requestID: ${requestID}"))
      updateSql = s"""UPDATE ${schemaName}.band_auth_request_table SET status = ? WHERE request_id = ?"""
      _ <- writeDB(
        updateSql,
        List(
          SqlParameter("String", RequestStatus.Approved.toString),
          SqlParameter("String", requestID)
        )
      )
      
      // Step 3.1.2: Notify AddBandManager API to authenticate the user as a band manager
      _ <- IO(logger.info(s"Calling AddBandManager for userID: ${userID}, bandID: ${bandID}"))
      _ <- AddBandManager(adminID, adminToken, userID, bandID).send
    } yield ()
  }

  private def rejectRequest()(using PlanContext): IO[Unit] = {
    for {
      // Step 3.2: Update request status to Rejected
      _ <- IO(logger.info(s"Rejecting request with requestID: ${requestID}"))
      updateSql = s"""UPDATE ${schemaName}.band_auth_request_table SET status = ? WHERE request_id = ?"""
      _ <- writeDB(
        updateSql,
        List(
          SqlParameter("String", RequestStatus.Rejected.toString),
          SqlParameter("String", requestID)
        )
      )
    } yield ()
  }
}