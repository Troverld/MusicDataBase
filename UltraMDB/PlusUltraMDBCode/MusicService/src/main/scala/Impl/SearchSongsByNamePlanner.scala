package Impl


import APIs.OrganizeService.ValidateUserMapping
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import cats.effect.IO
import io.circe.Json
import org.slf4j.LoggerFactory
import cats.implicits._
import Common.Serialize.CustomColumnTypes.{decodeDateTime, encodeDateTime}
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
import APIs.OrganizeService.ValidateUserMapping
import io.circe._
import io.circe.syntax._
import io.circe.generic.auto._
import cats.implicits.*
import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

case class SearchSongsByNamePlanner(
                                     userID: String,
                                     userToken: String,
                                     keywords: String,
                                     override val planContext: PlanContext
                                   ) extends Planner[(Option[List[String]], String)] {

  private val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  override def plan(using planContext: PlanContext): IO[(Option[List[String]], String)] = {
    (
      for {
        // Step 1: Validate user rights
        _ <- IO(logger.info(s"[Step 1] Validate user rights for userID=${userID}, userToken=${userToken}"))
        (isValid,msg) <- ValidateUserMapping(userID, userToken).send
        _ <- IO {
          if (!isValid)
            logger.error(s"[Step 1.1] User validation failed for userID=${userID}, userToken=${userToken}")
          else
            logger.info("[Step 1.1] User validation succeeded")
        }
        _ <- if (!isValid)
          IO.raiseError(new IllegalAccessException("User authentication invalid"))
        else IO.unit

        // Step 2: Check if keywords are empty
        _ <- IO(logger.info(s"[Step 2] Check if keywords are empty: ${keywords}"))
        result <- if (keywords.isEmpty) IO {
          logger.info("[Step 2.1] Keywords are empty. Returning an empty song list.")
          List.empty[String]
        } else {
          performSearch(keywords)
        }

        _ <- IO(logger.info(s"[Step 3] Search result: ${result}"))

      } yield (Some(result), "")  // 成功：匹配到的歌曲 ID 列表
      ).handleErrorWith { e =>
      IO(logger.error(s"搜索失败: ${e.getMessage}")) *>
        IO.pure((None, e.getMessage))  // 失败：返回错误信息
    }
  }

  /**
   * Database search operation: Fuzzy search song names by keywords.
   */
  private def performSearch(keywords: String)(using PlanContext): IO[List[String]] = {
    val sql =
      s"""
         |SELECT song_id
         |FROM ${schemaName}.song_table
         |WHERE name ILIKE ?;
       """.stripMargin

    val parameters = List(SqlParameter("String", s"%${keywords}%"))
    logger.info(s"[Step 3] Starting song search with keywords='${keywords}', SQL='${sql}', parameters='${parameters.map(_.value).mkString(", ")}'")

    readDBRows(sql, parameters).flatMap { rows =>
      IO {
        logger.info(s"[Step 3.1] Query result count=${rows.length}")
        rows.map(json => decodeField[String](json, "song_id"))
      }
    }
  }
}