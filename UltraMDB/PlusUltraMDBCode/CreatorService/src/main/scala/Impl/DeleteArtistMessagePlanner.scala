package Impl


import APIs.OrganizeService.ValidateAdminMapping
import Objects.MusicService.Song
import Objects.TrackService.Album
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import cats.effect.IO
import io.circe.Json
import org.slf4j.LoggerFactory
import io.circe.generic.auto._
import cats.implicits._
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
import Objects.TrackService.Album
import org.joda.time.DateTime
import io.circe._
import io.circe.syntax._
import cats.implicits.*
import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

case class DeleteArtistMessagePlanner(
                                       adminID: String,
                                       adminToken: String,
                                       artistID: String,
                                       override val planContext: PlanContext
                                     ) extends Planner[String] {
  val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  override def plan(using planContext: PlanContext): IO[String] = {
    for {
      // Step 1: Validate admin rights
      _ <- IO(logger.info("[Step 1] 验证管理员身份"))
      isValid <- ValidateAdminMapping(adminID, adminToken).send
      _ <- handleAdminValidation(isValid)

      // Step 2: Check if artistID exists
      _ <- IO(logger.info(s"[Step 2] 检查艺术家ID是否存在：artistID=${artistID}"))
      artistExists <- isArtistIDExist()
      _ <- handleArtistExistence(artistExists)

      // Step 3: Check if artistID is referenced
      _ <- IO(logger.info(s"[Step 3] 检查艺术家是否被引用：artistID=${artistID}"))
      isReferenced <- isArtistIDReferenced()
      _ <- handleArtistReference(isReferenced)

      // Step 4: Delete artist record
      _ <- IO(logger.info(s"[Step 4] 删除艺术家记录：artistID=${artistID}"))
      deleteResult <- deleteArtist()

    } yield deleteResult
  }
  
  // Helper function: Validate admin credentials
  private def handleAdminValidation(isValid: Boolean)(using PlanContext): IO[Unit] = {
    if (!isValid) {
      IO.raiseError(new IllegalStateException("[Step 1] 管理员认证失败"))
    } else IO.unit
  }

  // Helper function: Check existence of artistID
  private def isArtistIDExist()(using PlanContext): IO[Boolean] = {
    val sql = s"SELECT 1 FROM ${schemaName}.artist_table WHERE artist_id = ?;"
    readDBJsonOptional(sql, List(SqlParameter("String", artistID))).map(_.isDefined)
  }

  private def handleArtistExistence(exists: Boolean)(using PlanContext): IO[Unit] = {
    if (!exists) {
      IO.raiseError(new IllegalStateException("[Step 2] 艺术家ID不存在"))
    } else IO.unit
  }

  // Helper function: Check if artistID is referenced
  private def isArtistIDReferenced()(using PlanContext): IO[Boolean] = {
    for {
      songReferenced <- isReferencedInSongs()
      albumReferenced <- isReferencedInAlbums()
    } yield songReferenced || albumReferenced
  }

  private def isReferencedInSongs()(using PlanContext): IO[Boolean] = {
    val sql =
      s"""
        SELECT 1
        FROM ${schemaName}.song
        WHERE ? = ANY(creators) OR ? = ANY(performers)
      """
    readDBJsonOptional(sql, List(
      SqlParameter("String", artistID),
      SqlParameter("String", artistID)
    )).map(_.isDefined)
  }

  private def isReferencedInAlbums()(using PlanContext): IO[Boolean] = {
    val sql =
      s"""
        SELECT 1
        FROM ${schemaName}.album
        WHERE ? = ANY(creators)
      """
    readDBJsonOptional(sql, List(SqlParameter("String", artistID))).map(_.isDefined)
  }

  private def handleArtistReference(isReferenced: Boolean)(using PlanContext): IO[Unit] = {
    if (isReferenced) {
      IO.raiseError(new IllegalStateException("[Step 3] 艺术家已被引用，无法删除"))
    } else IO.unit
  }

  // Helper function: Delete artist record
  private def deleteArtist()(using PlanContext): IO[String] = {
    val sql =
      s"""
        DELETE FROM ${schemaName}.artist_table
        WHERE artist_id = ?
      """
    writeDB(sql, List(SqlParameter("String", artistID)))
  }
}