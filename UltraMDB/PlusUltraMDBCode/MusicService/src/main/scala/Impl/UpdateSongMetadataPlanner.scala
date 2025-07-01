package Impl


import Objects.CreatorService.{Artist, Band}
import Objects.MusicService.Genre
import APIs.MusicService.ValidateSongOwnership
import Objects.CreatorService.Artist
import APIs.OrganizeService.validateUserMapping
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import io.circe.Json
import org.joda.time.DateTime
import cats.effect.IO
import org.slf4j.LoggerFactory
import io.circe._
import io.circe.syntax._
import io.circe.generic.auto._
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
import Objects.CreatorService.Band
import APIs.OrganizeService.validateUserMapping
import cats.implicits.*
import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

case class UpdateSongMetadataPlanner(
    userID: String,
    userToken: String,
    songID: String,
    name: Option[String],
    releaseTime: Option[DateTime],
    creators: List[String],
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
        // Step 1: Validate userToken and userID
        _ <- IO(logger.info("Validating userToken and userID..."))
        (isValidToken,msg1) <- validateUserMapping(userID, userToken).send
        _ <- if (!isValidToken)
          IO.raiseError(new Exception("Invalid user token"))
        else IO.unit

        // Step 2: Validate song ownership
        _ <- IO(logger.info(s"Validating song ownership for userID=${userID}, songID=${songID}"))
        (isOwner,msg2) <- ValidateSongOwnership(userID, userToken, songID).send
        _ <- if (!isOwner)
          IO.raiseError(new Exception("User does not own this song"))
        else IO.unit

        // Step 3: Check if the song exists in the SongTable
        _ <- IO(logger.info(s"Checking if songID=${songID} exists in SongTable..."))
        songExists <- checkSongExists
        _ <- if (!songExists)
          IO.raiseError(new Exception("歌曲不存在"))
        else IO.unit

        // Step 4: Update the metadata
        _ <- IO(logger.info(s"Updating metadata for songID=${songID}..."))
        _ <- updateSongMetadata

        // Step 5: Validate updated data (if necessary)
        _ <- IO(logger.info(s"Validating updated song data for songID=${songID}..."))
        _ <- validateUpdatedData
      } yield (true, "")  // 成功：返回 true 和空错误信息
      ).handleErrorWith { e =>
      IO(logger.error(s"更新歌曲元数据失败: ${e.getMessage}")) *>
        IO.pure((false, e.getMessage))  // 失败：返回 false 和错误信息
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
      Some(updateIDListField("creators", creators)),
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

  private def updateGenres(genres: List[String])(using PlanContext): IO[Unit] = {
    IO(logger.info(s"Updating genres for songID=${songID} with genres: ${genres}...")) >>
      validateGenresExist(genres) >>
      writeDB(
        s"UPDATE ${schemaName}.song_table SET genres = ? WHERE song_id = ?;",
        List(SqlParameter("String", genres.asJson.noSpaces), SqlParameter("String", songID))
      ).void
  }

  private def validateIDsExist(ids: List[String])(using PlanContext): IO[Unit] = {
    ids.map { id =>
      val artistCheck = readDBJsonOptional(
        s"SELECT 1 FROM ${schemaName}.artist WHERE artist_id = ?;",
        List(SqlParameter("String", id))
      )
      val bandCheck = readDBJsonOptional(
        s"SELECT 1 FROM ${schemaName}.band WHERE band_id = ?;",
        List(SqlParameter("String", id))
      )
      for {
        artistExists <- artistCheck
        bandExists <- bandCheck
        _ <- if (artistExists.isEmpty && bandExists.isEmpty)
          IO.raiseError(new Exception(s"ID ${id} not found in Artist or Band"))
        else IO.unit
      } yield ()
    }.sequence_.void
  }

  private def validateGenresExist(genres: List[String])(using PlanContext): IO[Unit] = {
    genres.map { genreID =>
      readDBJsonOptional(
        s"SELECT 1 FROM ${schemaName}.genre WHERE genre_id = ?;",
        List(SqlParameter("String", genreID))
      ).flatMap {
        case Some(_) => IO.unit
        case None    => IO.raiseError(new Exception(s"Genre with ID ${genreID} does not exist"))
      }
    }.sequence_.void
  }

  private def validateUpdatedData(using PlanContext): IO[Unit] = {
    IO(logger.info(s"Running integrity checks for songID=${songID}...")) >>
      IO.unit // Add actual integrity checks here if necessary
  }
}