package Impl


import Objects.CreatorService.Band
import Objects.CreatorService.Artist
import APIs.OrganizeService.validateUserMapping
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import cats.effect.IO
import org.slf4j.LoggerFactory
import io.circe.Json
import io.circe._
import io.circe.syntax._
import io.circe.generic.auto._
import org.joda.time.DateTime
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

case class FilterSongsByEntityPlanner(
                                       entityID: Option[String],
                                       entityType: Option[String],
                                       genres: List[String],
                                       userID: String,
                                       userToken: String,
                                       override val planContext: PlanContext
                                     ) extends Planner[List[String]] {
  val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  override def plan(using planContext: PlanContext): IO[List[String]] = {
    for {
      // Step 1: Validate userToken and userID mapping
      _ <- IO(logger.info("Validating userToken and userID mapping"))
      isValid <- validateUserMapping(userID, userToken).send
      _ <- if (!isValid) IO.raiseError(new IllegalArgumentException("Invalid userToken or userID mapping")) else IO.unit

      // Step 2: Validate entityID if applicable
      _ <- IO(logger.info(s"Validating entityID for entityType: ${entityType}, entityID: ${entityID}"))
      _ <- validateEntityID

      // Step 3: Build filtering criteria for songs
      _ <- IO(logger.info("Building filter criteria for songs"))
      queryResult <- filterSongs

    } yield queryResult
  }

  private def validateEntityID(using PlanContext): IO[Unit] = {
    entityType match {
      case Some("artist") =>
        entityID match {
          case Some(id) => validateArtistID(id)
          case None => IO.unit // No entityID provided for validation
        }
      case Some("band") =>
        entityID match {
          case Some(id) => validateBandID(id)
          case None => IO.unit // No entityID provided for validation
        }
      case None => IO.unit // No entityType provided, skip validation
      case Some(_) => IO.raiseError(new IllegalArgumentException("Invalid entityType"))
    }
  }

  private def validateArtistID(artistID: String)(using PlanContext): IO[Unit] = {
    val sql = s"SELECT 1 FROM ${schemaName}.artist WHERE artist_id = ? LIMIT 1"
    readDBJsonOptional(sql, List(SqlParameter("String", artistID))).flatMap {
      case Some(_) => IO.unit
      case None => IO.raiseError(new IllegalArgumentException(s"Invalid artistID: ${artistID}"))
    }
  }

  private def validateBandID(bandID: String)(using PlanContext): IO[Unit] = {
    val sql = s"SELECT 1 FROM ${schemaName}.band WHERE band_id = ? LIMIT 1"
    readDBJsonOptional(sql, List(SqlParameter("String", bandID))).flatMap {
      case Some(_) => IO.unit
      case None => IO.raiseError(new IllegalArgumentException(s"Invalid bandID: ${bandID}"))
    }
  }

  private def filterSongs(using PlanContext): IO[List[String]] = {
    val sqlBuilder = new StringBuilder(s"SELECT song_id FROM ${schemaName}.song_table WHERE 1=1")
    val parameters = scala.collection.mutable.ListBuffer.empty[SqlParameter]

    // Add filter for entityType and entityID
    entityType match {
      case Some("artist") =>
        entityID.foreach { id =>
          sqlBuilder.append(" AND (creators @> ?::jsonb OR performers @> ?::jsonb)")
          parameters += SqlParameter("String", s"""["${id}"]""")
          parameters += SqlParameter("String", s"""["${id}"]""")
        }
      case Some("band") =>
        entityID.foreach { id =>
          sqlBuilder.append(" AND (creators @> ?::jsonb OR performers @> ?::jsonb)")
          parameters += SqlParameter("String", s"""["${id}"]""")
          parameters += SqlParameter("String", s"""["${id}"]""")
        }
      case _ => // Do nothing if entityType is None or invalid
    }

    // Add filter for genres
    if (genres.nonEmpty) {
      sqlBuilder.append(" AND genres && ?::jsonb")
      parameters += SqlParameter("String", genres.asJson.noSpaces)
    }

    val sql = sqlBuilder.toString()
    logger.info(s"Executing SQL query: ${sql} with parameters: ${parameters.mkString(", ")}")

    readDBRows(sql, parameters.toList).map { rows =>
      rows.map(json => decodeField[String](json, "song_id"))
    }
  }
}