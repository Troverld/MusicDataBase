package Impl


import APIs.OrganizeService.validateUserMapping
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import cats.effect.IO
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import io.circe.Json
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
import io.circe._
import io.circe.generic.auto._
import cats.implicits.*
import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

case class UserLoginMessagePlanner(userName: String, hashedPassword: String, override val planContext: PlanContext) extends Planner[Option[String]] {
  val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  override def plan(using PlanContext): IO[Option[String]] = {
    for {
      // Step 1: Check if the user exists and get its userID and stored password
      _ <- IO(logger.info(s"Checking if user exists in the database for userName: ${userName}"))
      userOptional <- getUserByUserName()

      // Step 2: Process login logic if user exists
      tokenOption <- userOptional match {
        case Some((userID, storedPassword)) =>
          if (storedPassword != hashedPassword) {
            IO(logger.warn(s"Password mismatch for userName: ${userName}")) *> IO(None)
          } else {
            for {
              // Step 3: Generate a new time-sensitive token
              _ <- IO(logger.info("Generating new user token"))
              newUserToken <- generateToken()

              // Step 4: Validate user mapping
              _ <- IO(logger.info(s"Validating user mapping for userID: ${userID} and token"))
              isValid <- validateUserMapping(userID, newUserToken).send

              // Step 5: Process mapping validation result
              finalTokenOption <- if (!isValid) {
                IO(logger.warn(s"User mapping validation failed for userID: ${userID}")) *> IO(None)
              } else {
                for {
                  // Step 6: Update the token invalidation time in the database
                  _ <- IO(logger.info(s"Updating token invalidation time for userID: ${userID}"))
                  _ <- updateUserInvalidationTime(userID)
                } yield Some(newUserToken)
              }
            } yield finalTokenOption
          }
        case None =>
          IO(logger.warn(s"No user found for userName: ${userName}")) *> IO(None)
      }
    } yield tokenOption
  }

  // Sub-function: Get user by userName
  private def getUserByUserName()(using PlanContext): IO[Option[(String, String)]] = {
    val query = s"""
      SELECT user_id, password
      FROM ${schemaName}.user_table
      WHERE account = ?;
    """
    val params = List(SqlParameter("String", userName))
    readDBJsonOptional(query, params).map {
      case Some(json) =>
        val userID = decodeField[String](json, "user_id")
        val storedPassword = decodeField[String](json, "password")
        Some((userID, storedPassword))
      case None => None
    }
  }

  // Sub-function: Generate a user token
  private def generateToken()(using PlanContext): IO[String] = {
    IO(java.util.UUID.randomUUID().toString)
  }

  // Sub-function: Update the user's token invalidation time
  private def updateUserInvalidationTime(userID: String)(using PlanContext): IO[Unit] = {
    val query = s"""
      UPDATE ${schemaName}.user_table
      SET invalid_time = ?
      WHERE user_id = ?;
    """
    val params = List(
      SqlParameter("DateTime", DateTime.now.getMillis.toString),
      SqlParameter("String", userID)
    )
    writeDB(query, params).void
  }
}