package Impl

import APIs.MusicService.ValidateSongOwnership
import Common.API.{PlanContext, Planner}
import APIs.OrganizeService.validateUserMapping
import cats.effect.IO
import Common.ServiceUtils.schemaName
import Common.DBAPI.readDBJsonOptional
import Objects.MusicService.Song
import io.circe.parser.decode
import org.slf4j.LoggerFactory
import Objects.CreatorService.Band
import Objects.CreatorService.Artist
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

case class GetSongByID(
                        userID: String,
                        userToken: String,
                        songID: String
                      ) extends Planner[(Option[Song], String)]{
  val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  override def plan(using planContext: PlanContext): IO[(Option[Song], String)] = {
    (
      for {
        (isValid,msg) <- validateUserMapping(userID, userToken).send
        _ <- if (!isValid)
          IO.raiseError(new Exception("User verification failed."))
        else IO.unit

        _ <- IO(logger.info(s"Checking if songID=${songID} exists in SongTable..."))
        songExists <- checkSongExists
        _ <- if (!songExists)
          IO.raiseError(new Exception("Song does not exists."))
        else IO.unit

        _ <- IO(logger.info(s"Getting song by songID=${songID} in SongTable."))
        songOpt <- getSongByID

      } yield songOpt  // 成功：返回 true 和空错误信息
      ).handleErrorWith { e =>
      IO(logger.error(s"更新歌曲元数据失败: ${e.getMessage}")) *>
        IO.pure((None, e.getMessage))  // 失败：返回 false 和错误信息
    }
  }

  private def checkSongExists(using PlanContext): IO[Boolean] = {
    readDBJsonOptional(
      s"SELECT 1 FROM ${schemaName}.song_table WHERE song_id = ?;",
      List(SqlParameter("String", songID))
    ).map(_.isDefined)
  }

  private def getSongByID(using PlanContext): IO[(Option[Song], String)] = {
    (
      readDBJsonOptional(
        s"SELECT * FROM $schemaName.song_table WHERE song_id = ?;",
        List(SqlParameter("String", songID))
      ).flatMap {
        case Some(json) =>
          val jsonStr = json.noSpaces // ✅ 转为 String 才能 decode
          io.circe.parser.decode[Song](jsonStr) match {
            case Right(song) => IO.pure((Some(song),""))
            case Left(err) => IO.raiseError(new Exception(s"解析歌曲数据失败: ${err.getMessage}"))
          }
        case None => IO.pure((None,s"未找到歌曲"))
      }
    ).handleErrorWith { e =>
      IO(logger.error(s"查找歌曲失败: ${e.getMessage}")) *>
        IO.pure((None, e.getMessage))  // 失败：返回 false 和错误信息
    }
  }

}
