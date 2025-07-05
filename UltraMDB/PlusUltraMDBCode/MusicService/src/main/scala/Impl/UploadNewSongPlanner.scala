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
import org.joda.time.DateTime
import io.circe.*
import io.circe.syntax.*
import io.circe.generic.auto.*
import cats.implicits.*
import Common.Serialize.CustomColumnTypes.{decodeDateTime, encodeDateTime}

case class UploadNewSongPlanner(
                                 userID: String,
                                 userToken: String,
                                 name: String,
                                 releaseTime: DateTime,
                                 creators: List[CreatorID_Type],
                                 performers: List[String],
                                 lyricists: List[String],
                                 arrangers: List[String],
                                 instrumentalists: List[String],
                                 genres: List[String],
                                 composers: List[String]
                               ) extends Planner[(Option[String], String)] {

  val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  override def plan(using planContext: PlanContext): IO[(Option[String], String)] = {
    (
      for {
        _ <- IO(logger.info(s"验证用户令牌与用户ID的关联关系：userID=${userID}, userToken=${userToken}"))
        (isValidUser, msg) <- validateUserMapping(userID, userToken).send
        _ <- if (!isValidUser) IO.raiseError(new IllegalArgumentException("Invalid userToken or userID association.")) else IO.unit

        _ <- IO(logger.info(s"验证歌曲名称是否为空：name=${name}"))
        _ <- if (name.isEmpty) IO.raiseError(new IllegalArgumentException("Song name cannot be empty.")) else IO.unit

        _ <- IO(logger.info("验证creators和performers字段中的每个ID是否存在于Artist或Band"))
        _ <- validateCreatorsExist(creators)
        _ <- validateArtistsOrBands("performer", performers)

        _ <- IO(logger.info("验证所有字段的格式和存在性"))
        _ <- validateArtistsOrBands("lyricist", lyricists)
        _ <- validateArtistsOrBands("composer", composers)
        _ <- validateArtistsOrBands("arranger", arrangers)
        _ <- validateArtistsOrBands("instrumentalist", instrumentalists)

        _ <- IO(logger.info("验证genres字段中的每个曲风ID是否存在"))
        _ <- validateGenres(genres)

        _ <- IO(logger.info("生成唯一的songID标识符"))
        songID <- IO(java.util.UUID.randomUUID().toString)

        _ <- IO(logger.info(s"将新歌曲信息存入SongTable，songID=${songID}"))
        _ <- insertSongIntoDB(
          songID, userID, name, releaseTime,
          creators, performers, lyricists,
          arrangers, instrumentalists, genres, composers
        )

        _ <- IO(logger.info(s"新歌曲上传成功，songID=${songID}"))
      } yield (Some(songID), "")
      ).handleErrorWith { e =>
      IO(logger.error(s"上传歌曲失败: ${e.getMessage}")) *>
        IO.pure((None, e.getMessage))
    }
  }

  private def validateCreatorsExist(creators: List[CreatorID_Type])(using PlanContext): IO[Unit] = {
    creators.traverse_ {
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

  private def validateArtistsOrBands(fieldName: String, ids: List[String])(using PlanContext): IO[Unit] = {
    ids.traverse_ { id =>
      for {
        (artistOpt, _) <- GetArtistByID(userID, userToken, id).send
        (bandOpt, _)   <- GetBandByID(userID, userToken, id).send
        _ <- if (artistOpt.isEmpty && bandOpt.isEmpty)
          IO.raiseError(new IllegalArgumentException(s"Invalid $fieldName ID: $id not found in Artist or Band."))
        else IO.unit
      } yield ()
    }
  }

  private def validateGenres(genreIDs: List[String])(using PlanContext): IO[Unit] = {
    genreIDs.traverse_ { genreID =>
      readDBJsonOptional(
        s"SELECT * FROM ${schemaName}.genre_table WHERE genre_id = ?",
        List(SqlParameter("String", genreID))
      ).flatMap {
        case Some(_) => IO.unit
        case None    => IO.raiseError(new IllegalArgumentException(s"Invalid genre ID: $genreID not found in GenreTable."))
      }
    }
  }

  private def insertSongIntoDB(songID: String,
                               userID: String,
                               name: String,
                               releaseTime: DateTime,
                               creators: List[CreatorID_Type],
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
