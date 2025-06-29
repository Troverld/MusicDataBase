package Impl


import Objects.CreatorService.Band
import APIs.OrganizeService.validateUserMapping
import Objects.OrganizeService.RequestStatus
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import org.slf4j.LoggerFactory
import io.circe.Json
import io.circe.syntax._
import cats.effect.IO
import cats.implicits._
import Common.Serialize.CustomColumnTypes.{decodeDateTime, encodeDateTime}
import org.joda.time.DateTime
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
import io.circe.generic.auto._
import cats.implicits.*
import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

case class SubmitBandAuthRequestMessagePlanner(
    userID: String,
    userToken: String,
    bandID: String,
    certification: String,
    override val planContext: PlanContext
) extends Planner[String] {

  val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  override def plan(using planContext: PlanContext): IO[String] = {
    for {
      // Step 1: Validate userID and userToken
      _ <- IO(logger.info(s"[Step 1] 验证用户令牌有效性 userID=${userID}, userToken=${userToken}"))
      isValid <- validateUserMapping(userID, userToken).send
      _ <- if (!isValid) IO.raiseError(new IllegalArgumentException("用户令牌无效")) else IO.unit

      // Step 2: Check bandID existence and ensure user is not already bound to the band
      _ <- IO(logger.info(s"[Step 2] 检查乐队 bandID=${bandID} 是否存在，以及用户是否已绑定"))
      bandOpt <- getBandByID(bandID)
      band <- bandOpt match {
        case Some(value) => IO(value)
        case None => IO.raiseError(new IllegalArgumentException("乐队ID不存在"))
      }
      _ <- if (band.managedBy.contains(userID)) 
          IO.raiseError(new IllegalArgumentException("用户已与该乐队绑定")) 
        else 
          IO.unit

      // Step 3: Validate certification
      _ <- IO(logger.info("[Step 3] 验证认证证据的合法性"))
      _ <- validateCertification(certification)

      // Step 4: Check for existing pending binding requests
      _ <- IO(logger.info("[Step 4] 检查是否存在待处理的绑定申请"))
      hasPendingRequest <- checkPendingRequest(userID, bandID)
      _ <- if (hasPendingRequest) 
          IO.raiseError(new IllegalArgumentException("已有待完成的绑定申请")) 
        else 
          IO.unit

      // Step 5: Generate a new requestID and insert the new record into BandAuthRequestTable
      _ <- IO(logger.info("[Step 5] 生成新的requestID, 并插入BandAuthRequestTable"))
      requestID <- generateAndInsertNewRequest(userID, bandID, certification)

      // Step 6: Return the generated requestID
      _ <- IO(logger.info(s"[Step 6] 返回生成的 requestID=${requestID}"))
    } yield requestID
  }

  private def getBandByID(bandID: String)(using PlanContext): IO[Option[Band]] = {
    val sql = s"SELECT * FROM ${schemaName}.band WHERE band_id = ?;"
    readDBJsonOptional(sql, List(SqlParameter("String", bandID)))
      .map(_.map(decodeType[Band]))
  }

  private def validateCertification(cert: String)(using PlanContext): IO[Unit] = {
    if (cert.trim.isEmpty || cert.length > 255) {
      IO.raiseError(new IllegalArgumentException("认证证据格式不合法"))
    } else {
      IO.unit
    }
  }

  private def checkPendingRequest(userID: String, bandID: String)(using PlanContext): IO[Boolean] = {
    val sql =
      s"""
        SELECT COUNT(*)
        FROM ${schemaName}.band_auth_request_table
        WHERE user_id = ? AND band_id = ? AND status = ?;
      """
    readDBInt(
      sql,
      List(
        SqlParameter("String", userID),
        SqlParameter("String", bandID),
        SqlParameter("String", RequestStatus.Pending.toString)
      )
    ).map(_ > 0) // Returns true if there are pending requests
  }

  private def generateAndInsertNewRequest(
      userID: String,
      bandID: String,
      certification: String
  )(using PlanContext): IO[String] = {
    val requestID = java.util.UUID.randomUUID().toString
    val sql =
      s"""
        INSERT INTO ${schemaName}.band_auth_request_table
        (request_id, user_id, band_id, certification, status)
        VALUES (?, ?, ?, ?, ?);
      """
    writeDB(
      sql,
      List(
        SqlParameter("String", requestID),
        SqlParameter("String", userID),
        SqlParameter("String", bandID),
        SqlParameter("String", certification),
        SqlParameter("String", RequestStatus.Pending.toString)
      )
    ).map(_ => requestID)
  }
}