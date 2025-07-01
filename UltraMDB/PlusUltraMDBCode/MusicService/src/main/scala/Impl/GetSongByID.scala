package Impl

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
    songID: String,
    override val planContext: PlanContext
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


        songOpt <- performSearch

      } yield songOpt  // 成功：返回 true 和空错误信息
      ).handleErrorWith { e =>
      IO(logger.error(s"查询歌曲数据失败: ${e.getMessage}")) *>
        IO.pure((None, e.getMessage))  // 失败：返回 false 和错误信息
    }
  }

  private def checkSongExists(using PlanContext): IO[Boolean] = {
    readDBJsonOptional(
      s"SELECT 1 FROM ${schemaName}.song_table WHERE song_id = ?;",
      List(SqlParameter("String", songID))
    ).map(_.isDefined)
  }


  private def performSearch(using PlanContext): IO[(Option[Song], String)] = {
    readDBJsonOptional(
      s"SELECT * FROM ${schemaName}.song_table WHERE song_id = ?;",
      List(SqlParameter("String", songID))
    ).flatMap {
      case Some(rawJson) =>
        // 需要修复的字段名列表
        val listFields = List("creators", "performers", "lyricists", "composers", "arrangers", "instrumentalists", "genres")

        val patchedJson = rawJson.mapObject { jsonObj =>
          val fixedLists = listFields.foldLeft(jsonObj) { case (acc, field) =>
            acc(field) match {
              case Some(jsonVal) if jsonVal.isString =>
                val jsonStr = jsonVal.asString.getOrElse("[]")
                io.circe.parser.parse(jsonStr) match {
                  case Right(arrayJson) if arrayJson.isArray =>
                    acc.add(field, arrayJson)
                  case _ => acc
                }
              case _ => acc
            }
          }

          // 修复 releaseTime（从字符串时间戳 -> 数字时间戳）
          val fixedTime = fixedLists("releaseTime") match {
            case Some(jsonVal) if jsonVal.isString =>
              jsonVal.asString.flatMap(str => scala.util.Try(str.toLong).toOption) match {
                case Some(timestamp) => fixedLists.add("releaseTime", Json.fromLong(timestamp))
                case None => fixedLists
              }
            case _ => fixedLists
          }

          fixedTime
        }

        decodeTypeIO[Song](patchedJson).attempt.map {
          case Right(song) => (Some(song), "")
          case Left(err) => (None, "Decoding failed: " + err.getMessage)
        }

      case None =>
        IO.pure((None, "Song not found."))
    }
  }

}