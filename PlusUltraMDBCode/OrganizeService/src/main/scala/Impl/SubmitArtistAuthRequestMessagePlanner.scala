package Impl


/**
 * Planner for SubmitArtistAuthRequestMessage: 用户提交艺术家绑定申请
 *
 * @param userID The ID of the user making the request
 * @param userToken The authentication token of the user
 * @param artistID The ID of the target artist
 * @param certification The certification evidence provided by the user
 * @param planContext Implicit execution context
 */
import Objects.OrganizeService.RequestStatus
import Objects.CreatorService.Artist
import APIs.OrganizeService.validateUserMapping
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import cats.effect.IO
import org.slf4j.LoggerFactory
import io.circe.Json
import io.circe.parser._
import io.circe._
import io.circe.generic.auto._
import org.joda.time.DateTime
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
import APIs.OrganizeService.validateUserMapping
import io.circe.syntax._
import cats.implicits.*
import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

case class SubmitArtistAuthRequestMessagePlanner(
  userID: String,
  userToken: String,
  artistID: String,
  certification: String,
  override val planContext: PlanContext
) extends Planner[String] {
  val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  override def plan(using planContext: PlanContext): IO[String] = {
    for {
      // Step 1: Validate user mapping
      _ <- IO(logger.info("Step 1: Validating user mapping"))
      isValid <- validateUserMapping(userID, userToken).send
      _ <- if (!isValid) 
        IO.raiseError(new Exception("用户令牌无效")) 
      else 
        IO(logger.info(s"User validation succeeded for userID=$userID"))

      // Step 2: Check artist existence and current binding
      _ <- IO(logger.info(s"Step 2: Checking artist existence and current binding for artistID=$artistID"))
      artistOpt <- getArtistByID()
      artist <- artistOpt match {
        case Some(artist) => IO.pure(artist)
        case None         => IO.raiseError(new Exception("艺术家ID不存在"))
      }
      _ <- if (artist.managedBy.contains(userID)) 
        IO.raiseError(new Exception("用户已与该艺术家绑定")) 
      else 
        IO(logger.info(s"Artist validation succeeded for artistID=$artistID"))

      // Step 3: Validate certification
      _ <- IO(logger.info(s"Step 3: Validating certification for artistID=$artistID"))
      _ <- if (certification.trim.isEmpty || certification.length > 255) 
        IO.raiseError(new Exception("认证证据格式不合法")) 
      else 
        IO(logger.info(s"Certification validation passed"))

      // Step 4: Check for pending requests
      _ <- IO(logger.info(s"Step 4: Checking for pending requests for userID=$userID and artistID=$artistID"))
      hasPendingRequest <- checkPendingRequest()
      _ <- if (hasPendingRequest) 
        IO.raiseError(new Exception("已有待完成的绑定申请")) 
      else 
        IO(logger.info(s"No pending requests found for userID=$userID and artistID=$artistID"))

      // Step 5: Insert new request
      _ <- IO(logger.info("Step 5: Inserting new request"))
      newRequestID <- insertNewRequest()
      _ <- IO(logger.info(s"Successfully created request with ID: $newRequestID"))

    } yield newRequestID
  }

  private def getArtistByID()(using PlanContext): IO[Option[Artist]] = {
    val sql =
      s"""
         SELECT *
         FROM ${schemaName}.artist
         WHERE artist_id = ?;
       """
    IO(logger.info(s"Querying artist by ID with SQL: $sql and artistID=$artistID")) >>
      readDBJsonOptional(sql, List(SqlParameter("String", artistID)))
        .map(_.map(decodeType[Artist]))
  }

  private def checkPendingRequest()(using PlanContext): IO[Boolean] = {
    val sql =
      s"""
         SELECT COUNT(1)
         FROM ${schemaName}.artist_auth_request_table
         WHERE user_id = ? AND artist_id = ? AND status = ?;
       """
    IO(logger.info(s"Querying pending requests with SQL: $sql for userID=$userID and artistID=$artistID")) >>
      readDBInt(sql, List(
        SqlParameter("String", userID),
        SqlParameter("String", artistID),
        SqlParameter("String", RequestStatus.Pending.toString)
      )).map(_ > 0)
  }

  private def insertNewRequest()(using PlanContext): IO[String] = {
    val newRequestID = java.util.UUID.randomUUID().toString
    val sql =
      s"""
         INSERT INTO ${schemaName}.artist_auth_request_table
         (request_id, user_id, artist_id, certification, status)
         VALUES (?, ?, ?, ?, ?);
       """
    IO(logger.info(s"Inserting new request with SQL: $sql and requestID=$newRequestID")) >>
      writeDB(
        sql,
        List(
          SqlParameter("String", newRequestID),
          SqlParameter("String", userID),
          SqlParameter("String", artistID),
          SqlParameter("String", certification),
          SqlParameter("String", RequestStatus.Pending.toString)
        )
      ).map(_ => newRequestID)
  }
}