package Impl


import APIs.CreatorService.{GetArtistByID, GetBandByID}
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
    GetArtistByID(userID, userToken, artistID).send.flatMap {
      case (Some(_), _) => IO.unit
      case (None, _) => IO.raiseError(new IllegalArgumentException(s"Invalid Artist ID: $artistID not found."))
    }
  }

  private def validateBandID(bandID: String)(using PlanContext): IO[Unit] = {
    GetBandByID(userID, userToken, bandID).send.flatMap {
      case (Some(_), _) => IO.unit
      case (None, _)    => IO.raiseError(new IllegalArgumentException(s"Invalid Band ID: $bandID not found."))
    }
  }

  private def validateCreatorsExist(creators: List[CreatorID_Type])(using PlanContext): IO[Unit] = {
    creator.traverse_ {
      case CreatorID_Type(creatorType, id) =>
        creatorType match {
          case CreatorType.Artist =>
            GetArtistByID(userID, userToken, id).send.flatMap {
              case (None, _) => IO.raiseError(new IllegalArgumentException(s"Invalid creator ID: $id (Artist not found)"))
              case _ => IO.unit
            }
          case CreatorType.Band =>
            GetBandByID(userID, userToken, id).send.flatMap {
              case (None, _) => IO.raiseError(new IllegalArgumentException(s"Invalid creator ID: $id (Band not found)"))
              case _ => IO.unit
            }
        }
    }
  }

  private def filterSongs(using PlanContext): IO[List[String]] = {
    val sqlBuilder = new StringBuilder(s"SELECT song_id FROM ${schemaName}.song_table WHERE 1=1")
    val parameters = scala.collection.mutable.ListBuffer.empty[SqlParameter]

    // 条件 1：creator 匹配任一字段
    creator.foreach { creatorID =>
      val idPattern = s"%${creatorID.id}%"
      sqlBuilder.append(
        s"""
           | AND (
           |   creators::text ILIKE ? OR
           |   performers::text ILIKE ? OR
           |   lyricists::text ILIKE ? OR
           |   composers::text ILIKE ? OR
           |   arrangers::text ILIKE ? OR
           |   instrumentalists::text ILIKE ?
           | )
         """.stripMargin
      )
      for (_ <- 1 to 6) parameters += SqlParameter("String", idPattern)
    }

    // 条件 2：genres 模糊匹配
    genres.foreach { genre =>
      val genrePattern = s"%$genre%"
      sqlBuilder.append(" AND genres::text ILIKE ?")
      parameters += SqlParameter("String", genrePattern)
    }

    val sql = sqlBuilder.toString()
    logger.info(s"Executing SQL: $sql with parameters: ${parameters.map(_.value).mkString(", ")}")

    readDBRows(sql, parameters.toList).map(_.map(decodeField[String](_, "song_id")))
  }
}
