package Impl


import APIs.OrganizeService.validateAdminMapping
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import cats.effect.IO
import org.slf4j.LoggerFactory
import org.joda.time.DateTime
import io.circe._
import io.circe.syntax._
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
import APIs.OrganizeService.validateAdminMapping
import io.circe.generic.auto._
import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

case class DeleteGenrePlanner(
                               genreID: String,
                               adminID: String,
                               adminToken: String,
                               override val planContext: PlanContext
                             ) extends Planner[String] {

  val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  override def plan(using PlanContext): IO[String] = {
    for {
      // Step 1: Validate admin identity and permissions
      _ <- IO(logger.info(s"Validating admin permissions for adminID: ${adminID}."))
      isAdminValid <- validateAdminMapping(adminID, adminToken).send
      _ <- if (!isAdminValid) {
        IO.raiseError(new IllegalAccessException(s"Admin validation failed for adminID $adminID"))
      } else IO.unit

      // Step 2: Check if genreID exists in GenreTable
      _ <- IO(logger.info(s"Checking if genreID ${genreID} exists in GenreTable."))
      genreExists <- checkGenreExistence(genreID)
      _ <- if (!genreExists) {
        IO.raiseError(new IllegalArgumentException("曲风不存在"))
      } else IO.unit

      // Step 3: Validate if the genre is referenced by any song
      _ <- IO(logger.info(s"Validating if genreID ${genreID} is referenced in SongTable."))
      isGenreReferenced <- checkGenreUsage(genreID)
      _ <- if (isGenreReferenced) {
        IO.raiseError(new IllegalStateException("曲风已被引用，无法删除"))
      } else IO.unit

      // Step 4: Delete genre from GenreTable
      _ <- IO(logger.info(s"Deleting genreID ${genreID} from GenreTable."))
      deleteResult <- deleteGenreByID(genreID)
      _ <- IO(logger.info(s"Delete operation result: $deleteResult."))
    } yield "删除成功"
  }

  /**
   * Step 2.1: Check if genreID exists in GenreTable
   */
  private def checkGenreExistence(genreID: String)(using PlanContext): IO[Boolean] = {
    val sql = s"SELECT COUNT(1) FROM ${schemaName}.genre_table WHERE genre_id = ?"
    val parameters = List(SqlParameter("String", genreID))
    readDBInt(sql, parameters).map(_ > 0) // Return true if count > 0
  }

  /**
   * Step 3.1: Validate if the genreID is referenced in SongTable (genres field)
   */
  private def checkGenreUsage(genreID: String)(using PlanContext): IO[Boolean] = {
    val sql = s"SELECT COUNT(1) FROM ${schemaName}.song_table WHERE genres LIKE ?"
    val parameters = List(SqlParameter("String", s"""%"$genreID"%""")) // Genres are stored as a JSON array
    readDBInt(sql, parameters).map(_ > 0) // Return true if count > 0
  }

  /**
   * Step 4.1: Delete genre from GenreTable by genreID
   */
  private def deleteGenreByID(genreID: String)(using PlanContext): IO[String] = {
    val sql = s"DELETE FROM ${schemaName}.genre_table WHERE genre_id = ?"
    val parameters = List(SqlParameter("String", genreID))
    writeDB(sql, parameters)
  }
}