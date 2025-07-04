package Impl

import APIs.OrganizeService.validateUserMapping
import Common.API.{PlanContext, Planner}
import Common.DBAPI.*
import Common.Object.SqlParameter
import Common.Serialize.CustomColumnTypes.{decodeDateTime, encodeDateTime}
import Common.ServiceUtils.schemaName
import Objects.CreatorService.{Artist, Band}
import Objects.MusicService.Song
import Objects.StatisticsService.Profile
import cats.effect.IO
import cats.implicits.*
import io.circe.*
import io.circe.generic.auto.*
import io.circe.parser.decode
import io.circe.syntax.*
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

case class GetSongProfile(
    userID: String,
    userToken: String,
    songID: String,
    override val planContext: PlanContext
) extends Planner[(Option[Profile], String)]{
  val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  override def plan(using planContext: PlanContext): IO[(Option[Profile], String)] = {
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


  private def performSearch(using PlanContext): IO[(Option[Profile], String)] = {
    for {
      // Step 1: 读取这首歌的 genres 字段
      songJsonOpt <- readDBJsonOptional(
        s"SELECT genres FROM ${schemaName}.song_table WHERE song_id = ?;",
        List(SqlParameter("String", songID))
      )

      songGenres <- songJsonOpt match {
        case Some(jsonObj) =>
          val field = jsonObj.hcursor.downField("genres")
          if (field.focus.exists(_.isString)) {
            // 如果是字符串，先解析成数组
            field.as[String].flatMap { rawStr =>
              io.circe.parser.parse(rawStr).flatMap(_.as[List[String]])
            } match {
              case Right(list) => IO.pure(list.toSet)
              case Left(err) => IO.raiseError(new Exception(s"解析歌曲 genres 字段失败: ${err.getMessage}"))
            }
          } else {
            field.as[List[String]] match {
              case Right(list) => IO.pure(list.toSet)
              case Left(err) => IO.raiseError(new Exception(s"读取歌曲 genres 数组失败: ${err.getMessage}"))
            }
          }

        case None =>
          IO.raiseError(new Exception("未找到歌曲"))
      }

      // Step 2: 读取所有 genre_id
      allGenresJson <- readDBJson(
        s"SELECT genre_id FROM ${schemaName}.genre_table;",
        List()
      )

      allGenres <- IO.fromEither(
        allGenresJson.as[List[Map[String, String]]].map(_.flatMap(_.get("genre_id")))
      )

      // Step 3: 构建 Profile 向量（genre 是否出现）
      profileVec = allGenres.map { gid =>
        gid -> (if (songGenres.contains(gid)) 1.0 else 0.0)
      }

    } yield (Some(Profile(profileVec, norm = false)), "")
  }


}