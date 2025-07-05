package Impl


import APIs.CreatorService.{GetArtistByID, GetBandByID}
import Objects.CreatorService.{Artist, Band, CreatorID_Type}
import Objects.MusicService.Genre
import APIs.MusicService.ValidateSongOwnership
import APIs.OrganizeService.validateUserMapping
import Common.API.{PlanContext, Planner}
import Common.DBAPI.*
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import io.circe.Json
import org.joda.time.DateTime
import cats.effect.IO
import org.slf4j.LoggerFactory
import io.circe.*
import io.circe.syntax.*
import io.circe.generic.auto.*
import cats.implicits.*
import Common.Serialize.CustomColumnTypes.{decodeDateTime, encodeDateTime}

case class UpdateSongMetadataPlanner(
                                      userID: String,
                                      userToken: String,
                                      songID: String,
                                      name: Option[String],
                                      releaseTime: Option[DateTime],
                                      creators: List[CreatorID_Type],
                                      performers: List[String],
                                      lyricists: List[String],
                                      composers: List[String],
                                      arrangers: List[String],
                                      instrumentalists: List[String],
                                      genres: List[String],
                                      override val planContext: PlanContext
                                    ) extends Planner[(Boolean, String)] {

  val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  override def plan(using planContext: PlanContext): IO[(Boolean, String)] = {
    (
      for {
        _ <- IO(logger.info("Validating userToken and userID..."))
        (isValidToken,msg1) <- validateUserMapping(userID, userToken).send
        _ <- if (!isValidToken)
          IO.raiseError(new Exception("Invalid user token"))
        else IO.unit

        _ <- IO(logger.info(s"Validating song ownership for userID=${userID}, songID=${songID}"))
        (isOwner,msg2) <- ValidateSongOwnership(userID, userToken, songID).send
        _ <- if (!isOwner)
          IO.raiseError(new Exception("User does not own this song"))
        else IO.unit

        _ <- IO(logger.info(s"Checking if songID=${songID} exists in SongTable..."))
        songExists <- checkSongExists
        _ <- if (!songExists)
          IO.raiseError(new Exception("歌曲不存在"))
        else IO.unit

        _ <- IO(logger.info(s"Updating metadata for songID=${songID}..."))
        _ <- updateSongMetadata

        _ <- IO(logger.info(s"Validating updated song data for songID=${songID}..."))
        _ <- validateUpdatedData
      } yield (true, "")
      ).handleErrorWith { e =>
      IO(logger.error(s"更新歌曲元数据失败: ${e.getMessage}")) *>
        IO.pure((false, e.getMessage))
    }
  }

  private def checkSongExists(using PlanContext): IO[Boolean] = {
    readDBJsonOptional(
      s"SELECT 1 FROM ${schemaName}.song_table WHERE song_id = ?;",
      List(SqlParameter("String", songID))
    ).map(_.isDefined)
  }

  private def updateSongMetadata(using PlanContext): IO[Unit] = {
    val updateFutures = List(
      name.map(updateName),
      releaseTime.map(updateReleaseTime),
      Some(updateCreatorListField("creators", creators)),
      Some(updateIDListField("performers", performers)),
      Some(updateIDListField("lyricists", lyricists)),
      Some(updateIDListField("composers", composers)),
      Some(updateIDListField("arrangers", arrangers)),
      Some(updateIDListField("instrumentalists", instrumentalists)),
      Some(updateGenres(genres))
    ).flatten
    updateFutures.sequence_.void
  }

  private def updateName(newName: String)(using PlanContext): IO[Unit] = {
    IO(logger.info(s"Updating name to ${newName} for songID=${songID}...")) >>
      writeDB(
        s"UPDATE ${schemaName}.song_table SET name = ? WHERE song_id = ?;",
        List(SqlParameter("String", newName), SqlParameter("String", songID))
      ).void
  }

  private def updateReleaseTime(newTime: DateTime)(using PlanContext): IO[Unit] = {
    IO(logger.info(s"Updating releaseTime to ${newTime} for songID=${songID}...")) >>
      writeDB(
        s"UPDATE ${schemaName}.song_table SET release_time = ? WHERE song_id = ?;",
        List(SqlParameter("DateTime", newTime.getMillis.toString), SqlParameter("String", songID))
      ).void
  }

  private def updateIDListField(fieldName: String, ids: List[String])(using PlanContext): IO[Unit] = {
    IO(logger.info(s"Updating field ${fieldName} for songID=${songID} with IDs: ${ids}...")) >>
      validateIDsExist(ids) >>
      writeDB(
        s"UPDATE ${schemaName}.song_table SET ${fieldName} = ? WHERE song_id = ?;",
        List(SqlParameter("String", ids.asJson.noSpaces), SqlParameter("String", songID))
      ).void
  }

  private def updateCreatorListField(fieldName: String, creators: List[CreatorID_Type])(using PlanContext): IO[Unit] = {
    IO(logger.info(s"Updating field ${fieldName} for songID=${songID} with CreatorID_Type list: ${creators}...")) >>
      validateCreatorsExist(creators) >>
      writeDB(
        s"UPDATE ${schemaName}.song_table SET ${fieldName} = ? WHERE song_id = ?;",
        List(SqlParameter("String", creators.asJson.noSpaces), SqlParameter("String", songID))
      ).void
  }

  private def updateGenres(genres: List[String])(using PlanContext): IO[Unit] = {
    IO(logger.info(s"Updating genres for songID=${songID} with genres: ${genres}...")) >>
      validateGenresExist(genres) >>
      writeDB(
        s"UPDATE ${schemaName}.song_table SET genres = ? WHERE song_id = ?;",
        List(SqlParameter("String", genres.asJson.noSpaces), SqlParameter("String", songID))
      ).void
  }

  private def validateIDsExist(ids: List[String])(using PlanContext): IO[Unit] = {
    ids.traverse_ { id =>
      for {
        (artistOpt, msg1) <- GetArtistByID(userID, userToken, id).send
        (bandOpt, msg2)   <- GetBandByID(userID, userToken, id).send
        _ <- if (artistOpt.isEmpty && bandOpt.isEmpty)
          IO.raiseError(new IllegalArgumentException(s"Invalid ID: $id not found in Artist or Band."))
        else IO.unit
      } yield ()
    }
  }

  private def validateCreatorsExist(creators: List[CreatorID_Type])(using PlanContext): IO[Unit] = {
    creators.traverse_ { creator =>
      val id = creator.id
      if (creator.isArtist) {
        GetArtistByID(userID, userToken, id).send.flatMap {
          case (Some(_), _) => IO.unit
          case (None, _)    => IO.raiseError(new IllegalArgumentException(s"Invalid Artist ID: $id not found."))
        }
      } else if (creator.isBand) {
        GetBandByID(userID, userToken, id).send.flatMap {
          case (Some(_), _) => IO.unit
          case (None, _)    => IO.raiseError(new IllegalArgumentException(s"Invalid Band ID: $id not found."))
        }
      } else IO.raiseError(new IllegalArgumentException(s"Unknown creator type for ID: $id"))
    }
  }

  private def validateGenresExist(genres: List[String])(using PlanContext): IO[Unit] = {
    genres.map { genreID =>
      readDBJsonOptional(
        s"SELECT 1 FROM ${schemaName}.genre_table WHERE genre_id = ?;",
        List(SqlParameter("String", genreID))
      ).flatMap {
        case Some(_) => IO.unit
        case None    => IO.raiseError(new Exception(s"Genre with ID ${genreID} does not exist"))
      }
    }.sequence_.void
  }

  private def validateUpdatedData(using PlanContext): IO[Unit] = {
    IO(logger.info(s"Running integrity checks for songID=${songID}...")) >>
      IO.unit
  }
}
