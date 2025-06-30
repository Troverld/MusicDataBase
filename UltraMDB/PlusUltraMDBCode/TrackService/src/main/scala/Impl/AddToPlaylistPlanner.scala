package Impl


import APIs.OrganizeService.validateUserMapping
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import cats.effect.IO
import org.slf4j.LoggerFactory
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
import io.circe.Json
import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

case class AddToPlaylistPlanner(
                                 playlistID: String,
                                 songIDs: List[String],
                                 userID: String,
                                 userToken: String,
                                 override val planContext: PlanContext
                               ) extends Planner[Boolean] {
  val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  override def plan(using PlanContext): IO[Boolean] = {
    for {
      // Step 1: Validate user token
      _ <- IO(logger.info("Validating user token..."))
      isValidToken <- validateUserMapping(userID, userToken).send
      _ <- if (!isValidToken) {
        IO(logger.error("Invalid user ID or token")) >>
          IO.raiseError(new IllegalArgumentException("Invalid user ID or token"))
      } else {
        IO(logger.info("User token validated successfully"))
      }

      // Step 2: Validate playlist ownership
      _ <- IO(logger.info(s"Validating ownership of playlist: $playlistID"))
      playlistOwner <- getPlaylistOwner()
      _ <- if (playlistOwner != userID) {
        IO(logger.error(s"Playlist does not belong to user: $userID")) >>
          IO.raiseError(new IllegalArgumentException("The playlist does not belong to the current user"))
      } else {
        IO(logger.info("Playlist ownership validated successfully"))
      }

      // Step 3: Add songs to the playlist
      _ <- IO(logger.info(s"Adding songs to playlist: $playlistID"))
      updatedContents <- addSongsToPlaylist()
      _ <- IO(logger.info(s"Songs added successfully. Updated playlist contents: $updatedContents"))
      
    } yield true
  }

  // Helper method to get the owner of the playlist
  private def getPlaylistOwner()(using PlanContext): IO[String] = {
    val sql =
      s"""
         SELECT owner_id
         FROM ${schemaName}.playlist_table
         WHERE playlist_id = ?;
       """
    readDBString(sql, List(SqlParameter("String", playlistID)))
  }

  // Helper method to add songs to the playlist
  private def addSongsToPlaylist()(using PlanContext): IO[String] = {
    val fetchSql =
      s"""
         SELECT contents
         FROM ${schemaName}.playlist_table
         WHERE playlist_id = ?;
       """
    readDBJsonOptional(fetchSql, List(SqlParameter("String", playlistID))).flatMap {
      case Some(json) =>
        // Decode the current contents (list of song IDs)
        val currentContents = decodeField[List[String]](json, "contents")
        logger.info(s"Current playlist contents: $currentContents")

        // Add new songs and remove duplicates
        val updatedContents = (currentContents ++ songIDs).distinct
        logger.info(s"Updated playlist contents: $updatedContents")

        val updateSql =
          s"""
             UPDATE ${schemaName}.playlist_table
             SET contents = ?
             WHERE playlist_id = ?;
           """
        writeDB(
          updateSql,
          List(
            SqlParameter("String", updatedContents.asJson.noSpaces),
            SqlParameter("String", playlistID)
          )
        ).map(_ => updatedContents.asJson.noSpaces)

      case None =>
        logger.error(s"Playlist with ID $playlistID not found")
        IO.raiseError(new IllegalArgumentException(s"Playlist with ID $playlistID not found"))
    }
  }
}