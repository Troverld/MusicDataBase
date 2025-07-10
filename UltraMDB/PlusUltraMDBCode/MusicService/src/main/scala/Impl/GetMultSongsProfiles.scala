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
import io.circe.parser.parse

case class GetMultSongsProfiles(
                                 userID: String,
                                 userToken: String,
                                 songIDs: List[String],
                                 override val planContext: PlanContext
                               ) extends Planner[(Option[List[Profile]], String)] {
  val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  override def plan(using planContext: PlanContext): IO[(Option[List[Profile]], String)] = {
    for {
      (isValid, msg) <- validateUserMapping(userID, userToken).send
      _ <- if (!isValid) IO.raiseError(new Exception("User verification failed.")) else IO.unit

      // Step 1: 获取所有 genre_id（统一维度）
      allGenreRows <- readDBRows(
        s"SELECT genre_id FROM ${schemaName}.genre_table;",
        List()
      )
      allGenres = allGenreRows.map(json => decodeField[String](json, "genre_id")).toList
      allGenreSet = allGenres.toSet

      // Step 2: 获取所有 song_id 和对应的 genres 字段
      placeholders = songIDs.map(_ => "?").mkString(", ")
      songRows <- readDBRows(
        s"SELECT song_id, genres FROM ${schemaName}.song_table WHERE song_id IN ($placeholders);",
        songIDs.map(id => SqlParameter("String", id))
      )

      // Step 3: 对每一首歌，解析其 genres，构建 Profile
      profiles <- songRows.traverse { json =>
        val id = decodeField[String](json, "song_id")
        val genreField = json.hcursor.downField("genres")

        val genreSet: Set[String] = if (genreField.focus.exists(_.isString)) {
          genreField.as[String]
            .flatMap(s => parse(s).flatMap(_.as[List[String]]))
            .getOrElse(Nil)
            .toSet
        } else {
          genreField.as[List[String]].getOrElse(Nil).toSet
        }

        // 构建向量
        val dims = allGenres.map { gid =>
          Dim(GenreID = gid, value = if (genreSet.contains(gid)) 1.0 else 0.0)
        }

        IO.pure(Profile(dims, norm = false))
      }

    } yield (Some(profiles), "")
  }

}
