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
import org.joda.time.DateTime
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

case class UploadNewSongPlanner(
                                 userID: String,
                                 userToken: String,
                                 name: String,
                                 releaseTime: DateTime,
                                 creators: List[String],
                                 performers: List[String],
                                 lyricists: List[String],
                                 arrangers: List[String],
                                 instrumentalists: List[String],
                                 genres: List[String],
                                 composers: List[String]
                               ) extends Planner[(Option[String], String)]{
  val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  override def plan(using planContext: PlanContext): IO[(Option[String], String)] = {
    (
      for {
        // Step 1: Validate user token and ID association
        _ <- IO(logger.info(s"验证用户令牌与用户ID的关联关系：userID=${userID}, userToken=${userToken}"))
        (isValidUser, msg) <- validateUserMapping(userID, userToken).send
        _ <- if (!isValidUser) IO.raiseError(new IllegalArgumentException("Invalid userToken or userID association.")) else IO.unit

        // Step 2: Validate song name is not empty
        _ <- IO(logger.info(s"验证歌曲名称是否为空：name=${name}"))
        _ <- if (name.isEmpty) IO.raiseError(new IllegalArgumentException("Song name cannot be empty.")) else IO.unit

        // Step 3: Validate creators and performers exist in Artist or Band
        _ <- IO(logger.info("验证creators和performers字段中的每个ID是否存在于Artist或Band"))
        _ <- validateArtistsOrBands("creator", creators)
        _ <- validateArtistsOrBands("performer", performers)

        // Step 4: Validate genres exist in GenreTable
        _ <- IO(logger.info("验证genres字段中的每个曲风ID是否存在"))
        _ <- validateGenres(genres)

        // Step 5: Validate optional fields
        _ <- IO(logger.info("验证所有可选字段的格式和存在性"))
        _ <- validateArtistsOrBands("lyricist", lyricists)
        _ <- validateArtistsOrBands("composer", composers)
        _ <- validateArtistsOrBands("arranger", arrangers)
        _ <- validateArtistsOrBands("instrumentalist", instrumentalists)

        // Step 6: Generate unique songID
        _ <- IO(logger.info("生成唯一的songID标识符"))
        songID <- IO(java.util.UUID.randomUUID().toString)

        // Step 7: Insert new song into SongTable
        _ <- IO(logger.info(s"将新歌曲信息存入SongTable，songID=${songID}"))
        _ <- insertSongIntoDB(
          songID, userID, name, releaseTime,
          creators, performers, lyricists,
          arrangers, instrumentalists, genres, composers
        )

        // Step 8: Return songID
        _ <- IO(logger.info(s"新歌曲上传成功，songID=${songID}"))
      } yield (Some(songID), "") // 成功：返回 Some(id), 空字符串
      ).handleErrorWith { e =>
      IO(logger.error(s"上传歌曲失败: ${e.getMessage}")) *>
        IO.pure((None, e.getMessage)) // 失败：返回 None 和错误信息
    }
  }

  private def validateArtistsOrBands(fieldName: String, ids: List[String])(using PlanContext): IO[Unit] = {
    ids.traverse_ { id =>
      for {
        bandExists <- readDBJsonOptional(
          s"SELECT * FROM ${schemaName}.band WHERE band_id = ?",
          List(SqlParameter("String", id))
        ).map(_.isDefined)
        artistExists <- readDBJsonOptional(
          s"SELECT * FROM ${schemaName}.artist WHERE artist_id = ?",
          List(SqlParameter("String", id))
        ).map(_.isDefined)
        _ <- if (!bandExists && !artistExists)
          IO.raiseError(new IllegalArgumentException(s"Invalid $fieldName ID: $id not found in Artist or Band."))
        else IO.unit
      } yield ()
    }
  }

  private def validateGenres(genreIDs: List[String])(using PlanContext): IO[Unit] = {
    genreIDs.traverse_ { genreID =>
      for {
        genreExists <- readDBJsonOptional(
          s"SELECT * FROM ${schemaName}.genre_table WHERE genre_id = ?",
          List(SqlParameter("String", genreID))
        ).map(_.isDefined)
        _ <- if (!genreExists)
          IO.raiseError(new IllegalArgumentException(s"Invalid genre ID: $genreID not found in GenreTable."))
        else IO.unit
      } yield ()
    }
  }

  private def insertSongIntoDB(songID: String,
                               userID: String,
                               name: String,
                               releaseTime: DateTime,
                               creators: List[String],
                               performers: List[String],
                               lyricists: List[String],
                               arrangers: List[String],
                               instrumentalists: List[String],
                               genres: List[String],
                               composers: List[String])(using PlanContext): IO[Unit] = {
    val params = List(
      SqlParameter("String", songID),
      SqlParameter("String", name),
      SqlParameter("DateTime", releaseTime.getMillis.toString),
      SqlParameter("String", creators.asJson.noSpaces),
      SqlParameter("String", performers.asJson.noSpaces),
      SqlParameter("String", lyricists.asJson.noSpaces),
      SqlParameter("String", composers.asJson.noSpaces),
      SqlParameter("String", arrangers.asJson.noSpaces),
      SqlParameter("String", instrumentalists.asJson.noSpaces),
      SqlParameter("String", genres.asJson.noSpaces),
      SqlParameter("String", userID)
    )

    writeDB(
      s"""
        INSERT INTO ${schemaName}.song_table
        (song_id, name, release_time, creators, performers, lyricists, composers, arrangers, instrumentalists, genres, uploader_id)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      """,
      params
    ).void
  }
}