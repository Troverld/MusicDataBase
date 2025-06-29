package Impl


import APIs.TrackService.validateAlbumOwnership
import Objects.CreatorService.Band
import Objects.MusicService.Song
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import cats.effect.IO
import org.slf4j.LoggerFactory
import io.circe.Json
import io.circe.syntax._
import io.circe.generic.auto._
import org.joda.time.DateTime
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
import Objects.CreatorService.Artist
import Objects.CreatorService.Artist
import Common.API.{PlanContext, Planner}
import Common.Object.ParameterList
import io.circe._
import cats.implicits.*
import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

case class UpdateAlbumPlanner(
                               albumID: String,
                               name: Option[String],
                               description: Option[String],
                               contents: List[String],
                               collaborators: List[String],
                               userID: String,
                               userToken: String,
                               override val planContext: PlanContext
                             ) extends Planner[(Boolean, String)] {

  val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  override def plan(using PlanContext): IO[(Boolean, String)] = {
    for {
      // Step 1: Validate album ownership
      _ <- IO(logger.info(s"验证用户 ${userID} 是否对专辑 ${albumID} 具有管理权限"))
      hasPermission <- validateAlbumOwnership(userID, userToken, albumID).send
      _ <- if (!hasPermission)
        IO.raiseError(new IllegalAccessException(s"用户 ${userID} 无权更新专辑 ${albumID}"))
      else IO.unit

      // Step 2: Validate and process fields
      _ <- IO(logger.info("开始验证和处理输入字段"))
      _ <- validateName()
      _ <- validateDescription()
      _ <- validateContents()
      _ <- validateCollaborators()

      // Step 3: Update AlbumTable
      _ <- IO(logger.info(s"开始更新专辑 ${albumID} 的信息"))
      _ <- updateAlbumRecord()

      // Log success
      _ <- IO(logger.info(s"成功更新专辑 ${albumID}"))
    } yield (true, s"专辑 ${albumID} 更新成功")
  }

  private def validateName()(using PlanContext): IO[Unit] = {
    name match {
      case Some(albumName) =>
        for {
          _ <- IO(logger.info(s"验证新名称 ${albumName} 是否存在重复"))
          sql =
            s"""
               SELECT COUNT(*)
               FROM ${schemaName}.album_table
               WHERE name = ? AND album_id != ?;
             """
          count <- readDBInt(sql, List(SqlParameter("String", albumName), SqlParameter("String", albumID)))
          _ <- if (count > 0)
            IO.raiseError(new IllegalArgumentException(s"名称 ${albumName} 已被占用"))
          else IO.unit
        } yield ()
      case None => IO.unit
    }
  }

  private def validateDescription()(using PlanContext): IO[Unit] = {
    description match {
      case Some(_) =>
        IO(logger.info("专辑描述合法，直接更新"))
      case None => IO.unit
    }
  }

  private def validateContents()(using PlanContext): IO[Unit] = {
    if (contents.nonEmpty) {
      for {
        _ <- IO(logger.info(s"验证专辑内容字段，检查歌曲 IDs: ${contents.mkString(", ")} 是否有效"))
        invalidSongs <- contents.filterA(isInvalidSong)
        _ <- if (invalidSongs.nonEmpty)
          IO.raiseError(new IllegalArgumentException(s"以下歌曲ID无效: ${invalidSongs.mkString(", ")}"))
        else IO.unit
      } yield ()
    } else IO.unit
  }

  private def isInvalidSong(songID: String)(using PlanContext): IO[Boolean] = {
    val sql =
      s"""
         SELECT COUNT(*)
         FROM ${schemaName}.song
         WHERE song_id = ?;
       """
    readDBInt(sql, List(SqlParameter("String", songID))).map(_ == 0) // Returns true if invalid
  }

  private def validateCollaborators()(using PlanContext): IO[Unit] = {
    if (collaborators.nonEmpty) {
      for {
        _ <- IO(logger.info(s"验证协作者 IDs: ${collaborators.mkString(", ")} 是否有效"))
        invalidCollaborators <- collaborators.filterA(isInvalidCollaborator)
        _ <- if (invalidCollaborators.nonEmpty)
          IO.raiseError(new IllegalArgumentException(s"以下协作者ID无效: ${invalidCollaborators.mkString(", ")}"))
        else IO.unit
      } yield ()
    } else IO.unit
  }

  private def isInvalidCollaborator(collaboratorID: String)(using PlanContext): IO[Boolean] = {
    val artistSql =
      s"""
         SELECT COUNT(*)
         FROM ${schemaName}.artist
         WHERE artist_id = ?;
       """
    val bandSql =
      s"""
         SELECT COUNT(*)
         FROM ${schemaName}.band
         WHERE band_id = ?;
       """
    for {
      isInvalidAsArtist <- readDBInt(artistSql, List(SqlParameter("String", collaboratorID))).map(_ == 0)
      isInvalidAsBand <- readDBInt(bandSql, List(SqlParameter("String", collaboratorID))).map(_ == 0)
    } yield isInvalidAsArtist && isInvalidAsBand
  }

  private def updateAlbumRecord()(using PlanContext): IO[Unit] = {
    val updates = List(
      name.map(n => s"name = ?" -> SqlParameter("String", n)),
      description.map(d => s"description = ?" -> SqlParameter("String", d)),
      if (contents.nonEmpty) Some("contents = ?" -> SqlParameter("Array[String]", contents.asJson.noSpaces)) else None,
      if (collaborators.nonEmpty)
        Some("collaborators = ?" -> SqlParameter("Array[String]", collaborators.asJson.noSpaces))
      else None
    ).flatten

    if (updates.nonEmpty) {
      val (setClauses, params) = updates.unzip
      val sql =
        s"""
           UPDATE ${schemaName}.album_table
           SET ${setClauses.mkString(", ")}
           WHERE album_id = ?;
         """
      writeDB(sql, params :+ SqlParameter("String", albumID)).void
    } else IO.unit
  }
}