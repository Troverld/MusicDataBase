package Impl

import APIs.OrganizeService.validateUserMapping
import Common.API.{PlanContext, Planner}
import Common.DBAPI.*
import Common.Object.SqlParameter
import Common.Serialize.CustomColumnTypes.{decodeDateTime, encodeDateTime}
import Common.ServiceUtils.schemaName
import Objects.StatisticsService.{Dim, Profile}
import cats.effect.IO
import cats.implicits.*
import io.circe.*
import io.circe.generic.auto.*
import io.circe.parser.decode
import org.slf4j.LoggerFactory

case class GetMultSongsProfiles(
                                 userID: String,
                                 userToken: String,
                                 songIDs: List[String],
                                 override val planContext: PlanContext
                               ) extends Planner[(Option[List[Profile]], String)] {
  val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  override def plan(using planContext: PlanContext): IO[(Option[List[Profile]], String)] = {
    (
      for {
        (isValid, msg) <- validateUserMapping(userID, userToken).send
        _ <- if (!isValid) IO.raiseError(new Exception("User verification failed.")) else IO.unit

        _ <- IO(logger.info(s"Fetching genre list for profile vector construction..."))
        allGenreRows <- readDBRows(
          s"SELECT genre_id FROM ${schemaName}.genre_table;",
          List()
        )
        allGenres = allGenreRows.map(json => decodeField[String](json, "genre_id")).toSet

        _ <- IO(logger.info(s"Fetching genres for ${songIDs.length} songs..."))
        placeholders = songIDs.map(_ => "?").mkString(",")
        genreRows <- readDBRows(
          s"SELECT song_id, genres FROM ${schemaName}.song_table WHERE song_id IN ($placeholders);",
          songIDs.map(SqlParameter("String", _))
        )

        songProfiles <- genreRows.traverse { jsonObj =>
          val cursor = jsonObj.hcursor
          for {
            songID <- IO.fromEither(cursor.get[String]("song_id"))
            genresField = cursor.downField("genres")
            genreSet <- if (genresField.focus.exists(_.isString)) {
              genresField.as[String].flatMap { rawStr =>
                io.circe.parser.parse(rawStr).flatMap(_.as[List[String]])
              } match {
                case Right(list) => IO.pure(list.toSet)
                case Left(err) => IO.raiseError(new Exception(s"Failed to parse genres for $songID: ${err.getMessage}"))
              }
            } else {
              genresField.as[List[String]] match {
                case Right(list) => IO.pure(list.toSet)
                case Left(err) => IO.raiseError(new Exception(s"Failed to read genres array for $songID: ${err.getMessage}"))
              }
            }

            profileVec = allGenres.toList.map { gid =>
              Dim(GenreID = gid, value = if (genreSet.contains(gid)) 1.0 else 0.0)
            }
          } yield Profile(profileVec, norm = false)
        }
      } yield (Some(songProfiles), "")
      ).handleErrorWith { e =>
      IO(logger.error(s"获取多个歌曲 Profile 失败: ${e.getMessage}")) *>
        IO.pure((None, e.getMessage))
    }
  }
}
