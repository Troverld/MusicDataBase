package Impl


import APIs.OrganizeService.validateAdminMapping
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import cats.effect.IO
import org.slf4j.LoggerFactory
import io.circe.syntax._
import org.joda.time.DateTime
import cats.syntax.all._
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
import io.circe.Json
import io.circe._
import io.circe.generic.auto._
import cats.implicits.*
import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

case class CreateBandMessagePlanner(
                                     adminID: String,
                                     adminToken: String,
                                     name: String,
                                     members: List[String],
                                     bio: String,
                                     override val planContext: PlanContext
                                   ) extends Planner[String] {

  val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  override def plan(using planContext: PlanContext): IO[String] = {
    for {
      // Step 1: Validate the adminToken and adminID mapping for administrator privileges
      _ <- IO(logger.info(s"Validating adminToken and adminID mapping for adminID: ${adminID}"))
      isAdminValid <- validateAdminMapping(adminID, adminToken).send
      _ <- if (!isAdminValid) IO.raiseError(new Exception("管理员认证失败")) else IO(logger.info("Admin validation successful"))

      // Step 2: Check if the name is not empty
      _ <- IO(logger.info("Validating band name is not empty"))
      _ <- if (name.isEmpty) IO.raiseError(new Exception("乐队名称不能为空")) else IO.unit

      // Step 3: Validate each artistID in members
      _ <- IO(logger.info(s"Validating members: ${members.mkString(",")}"))
      areAllArtistsValid <- validateArtists(members)
      _ <- if (!areAllArtistsValid) IO.raiseError(new Exception("部分艺术家ID无效")) else IO.unit

      // Step 4: Generate a unique bandID
      _ <- IO(logger.info("Generating unique bandID"))
      bandID <- generateUniqueBandID()

      // Step 5: Insert the new band into BandTable
      _ <- IO(logger.info(s"Inserting new band with bandID: ${bandID}"))
      _ <- insertNewBand(bandID)

      // Step 6: Return the bandID
      _ <- IO(logger.info(s"Successfully created band with bandID: ${bandID}"))
    } yield bandID
  }

  /**
   * Validates the list of artist IDs in the members parameter to ensure all IDs are valid.
   */
  private def validateArtists(members: List[String])(using PlanContext): IO[Boolean] = {
    val sql =
      s"""
SELECT artist_id
FROM ${schemaName}.artist_table
WHERE artist_id = ANY(?);
""".stripMargin

    readDBRows(sql, List(SqlParameter("Array[String]", members.asJson.noSpaces))).map { results =>
      val validArtistIDs = results.map(json => decodeField[String](json, "artist_id")).toSet
      val invalidArtists = members.filterNot(validArtistIDs.contains)
      logger.info(s"Invalid artist IDs: ${invalidArtists.mkString(",")}")
      invalidArtists.isEmpty
    }
  }

  /**
   * Generates a unique bandID for the new band.
   */
  private def generateUniqueBandID()(using PlanContext): IO[String] = {
    val randomID = java.util.UUID.randomUUID().toString
    IO(randomID)
  }

  /**
   * Inserts a new band entry into the BandTable.
   */
  private def insertNewBand(bandID: String)(using PlanContext): IO[Unit] = {
    val sql =
      s"""
INSERT INTO ${schemaName}.band_table (band_id, name, members, bio, managed_by)
VALUES (?, ?, ?, ?, ?);
""".stripMargin

    writeDB(sql, List(
      SqlParameter("String", bandID),
      SqlParameter("String", name),
      SqlParameter("Array[String]", members.asJson.noSpaces),
      SqlParameter("String", bio),
      SqlParameter("Array[String]", List(adminID).asJson.noSpaces)
    )).void
  }
}