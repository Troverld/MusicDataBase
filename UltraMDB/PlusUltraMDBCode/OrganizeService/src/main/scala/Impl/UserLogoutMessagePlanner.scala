package Impl


import APIs.OrganizeService.validateUserMapping
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import cats.effect.IO
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import io.circe._
import io.circe.syntax._
import io.circe.generic.auto._
import cats.implicits.*
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
import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

case class UserLogoutMessagePlanner(
                                     userID: String,
                                     userToken: String,
                                     override val planContext: PlanContext
                                   ) extends Planner[String] {
  val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  override def plan(using planContext: PlanContext): IO[String] = {
    for {
      // Step 1: Validate user mapping using validateUserMapping API
      _ <- IO(logger.info(s"Validating user mapping for userID: ${userID} with token: ${userToken}"))
      isValid <- validateUserMapping(userID, userToken).send
      _ <- IO(logger.info(s"Validation result for userID: ${userID} is ${isValid}"))

      // Step 2: If validation succeeded, update the invalid_time in UserTable
      result <- if (isValid) {
        updateInvalidTime(userID)
      } else {
        IO(logger.info(s"UserID: ${userID} validation failed. Returning failure result."))
          .as("Failure: Invalid user mapping or token")
      }
    } yield result
  }

  private def updateInvalidTime(userID: String)(using PlanContext): IO[String] = {
    val currentTime = DateTime.now()
    logger.info(s"Updating invalid_time to ${currentTime} for userID: ${userID}")

    val sql =
      s"""
        UPDATE ${schemaName}.user_table
        SET invalid_time = ?
        WHERE user_id = ?;
      """

    writeDB(sql, List(
      SqlParameter("DateTime", currentTime.getMillis.toString),
      SqlParameter("String", userID)
    )).map { _ =>
      logger.info(s"Invalid time updated successfully for userID: ${userID}")
      "Success"
    }.handleErrorWith { err =>
      logger.error(s"Error updating invalid_time for userID: ${userID}. Error: ${err.getMessage}", err)
      IO.pure("Failure: Could not update invalid_time")
    }
  }
}