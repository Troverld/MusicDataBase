package Impl


import Objects.CreatorService.{Artist, Band, CreatorID_Type, CreatorType}
import APIs.OrganizeService.validateUserMapping
import Common.API.{PlanContext, Planner}
import Common.DBAPI.*
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import cats.effect.IO
import org.slf4j.LoggerFactory
import io.circe.Json
import io.circe.*
import io.circe.syntax.*
import io.circe.generic.auto.*
import org.joda.time.DateTime
import cats.implicits.*
import Common.Serialize.CustomColumnTypes.{decodeDateTime, encodeDateTime}
import io.circe.*
import io.circe.syntax.*
import io.circe.generic.auto.*
import org.joda.time.DateTime
import cats.implicits.*
import Common.DBAPI.*
import Common.API.{PlanContext, Planner}
import cats.effect.IO
import Common.Object.SqlParameter
import Common.Serialize.CustomColumnTypes.{decodeDateTime, encodeDateTime}
import Common.ServiceUtils.schemaName
import APIs.OrganizeService.validateUserMapping
import Common.Serialize.CustomColumnTypes.{decodeDateTime, encodeDateTime}

case class FilterSongsByEntityPlanner(
                                       userID: String,
                                       userToken: String,
                                       creator: Option[CreatorID_Type],
                                       genres: Option[String],
                                       override val planContext: PlanContext
                                     ) extends Planner[(Option[List[String]], String)] {

  val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  override def plan(using planContext: PlanContext): IO[(Option[List[String]], String)] = {
    (
      for {
        // Step 1: Validate userToken and userID mapping
        _ <- IO(logger.info("Validating userToken and userID mapping"))
        (isValid, _) <- validateUserMapping(userID, userToken).send
        _ <- if (!isValid)
          IO.raiseError(new IllegalArgumentException("Invalid userToken or userID mapping"))
        else IO.unit

        // Step 2: Validate creator ID if provided
        _ <- creator match {
          case Some(CreatorID_Type(CreatorType.Artist, id)) => validateArtistID(id)
          case Some(CreatorID_Type(CreatorType.Band, id))   => validateBandID(id)
          case None => IO.unit
        }

        // Step 3: Query songs
        songIDs <- filterSongs
        _ <- IO(logger.info(s"Filtered songs: ${songIDs}"))

      } yield (Some(songIDs), "")
      ).handleErrorWith { e =>
      IO(logger.error(s"FilterSongsByEntity failed: ${e.getMessage}")) *>
        IO.pure((None, e.getMessage))
    }
  }

  private def validateArtistID(artistID: String)(using PlanContext): IO[Unit] = {
    readDBJsonOptional(
      s"SELECT 1 FROM ${schemaName}.artist WHERE artist_id = ? LIMIT 1",
      List(SqlParameter("String", artistID))
    ).flatMap {
      case Some(_) => IO.unit
      case None    => IO.raiseError(new IllegalArgumentException(s"Invalid artistID: $artistID"))
    }
  }

  private def validateBandID(bandID: String)(using PlanContext): IO[Unit] = {
    readDBJsonOptional(
      s"SELECT 1 FROM ${schemaName}.band WHERE band_id = ? LIMIT 1",
      List(SqlParameter("String", bandID))
    ).flatMap {
      case Some(_) => IO.unit
      case None    => IO.raiseError(new IllegalArgumentException(s"Invalid bandID: $bandID"))
    }
  }

  private def filterSongs(using PlanContext): IO[List[String]] = {
    val sqlBuilder = new StringBuilder(s"SELECT song_id FROM ${schemaName}.song_table WHERE 1=1")
    val parameters = scala.collection.mutable.ListBuffer.empty[SqlParameter]

    // Filter by creator (creators or performers contain the full encoded CreatorID_Type)
    creator.foreach { creatorID =>
      val jsonStr = List(creatorID).asJson.noSpaces
      sqlBuilder.append(" AND (creators @> ?::jsonb OR performers @> ?::jsonb)")
      parameters += SqlParameter("String", jsonStr)
      parameters += SqlParameter("String", jsonStr)
    }

    // Filter by genres
    genres.foreach { genreID =>
      val genreJsonStr = List(genreID).asJson.noSpaces
      sqlBuilder.append(" AND genres @> ?::jsonb")
      parameters += SqlParameter("String", genreJsonStr)
    }

    val sql = sqlBuilder.toString()
    logger.info(s"Executing SQL: $sql with parameters: ${parameters.mkString(", ")}")

    readDBRows(sql, parameters.toList).map(_.map(decodeField[String](_, "song_id")))
  }
}
