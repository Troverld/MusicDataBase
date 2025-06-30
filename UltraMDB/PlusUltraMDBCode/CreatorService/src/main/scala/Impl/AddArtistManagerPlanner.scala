package Impl


import Objects.OrganizeService.User
import APIs.OrganizeService.ValidateAdminMapping
import Objects.CreatorService.Artist
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import cats.effect.IO
import org.slf4j.LoggerFactory
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
import Objects.CreatorService.Artist
import io.circe.Json
import io.circe._
import org.joda.time.DateTime
import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

case class AddArtistManagerMessage(
                                    adminID: String,
                                    adminToken: String,
                                    userID: String,
                                    artistID: String
                                  ) extends API[String]("AddArtistManagerService")

case class AddArtistManagerPlanner(
                                    adminID: String,
                                    adminToken: String,
                                    userID: String,
                                    artistID: String,
                                    override val planContext: PlanContext
                                  ) extends Planner[String] {

  val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  override def plan(using PlanContext): IO[String] = {
    for {
      // Step 1: Validate admin token and admin ID
      _ <- IO(logger.info(s"验证管理员Token和ID之间的匹配关系：adminID=${adminID}, adminToken=${adminToken}"))
      adminValidation <- ValidateAdminMapping(adminID, adminToken).send
      _ <- if (!adminValidation)
        IO.raiseError(new IllegalStateException("管理员认证失败"))
      else
        IO(logger.info("管理员认证通过"))

      // Step 2: Check if the userID exists
      _ <- IO(logger.info(s"检查User表中是否存在用户ID：userID=${userID}"))
      userExists <- checkUserExists(userID)
      _ <- if (!userExists)
        IO.raiseError(new IllegalStateException("当前用户不存在"))
      else
        IO(logger.info(s"用户(userID=${userID})存在"))

      // Step 3: Fetch artist and check if the artistID exists
      _ <- IO(logger.info(s"检查Artist表中是否存在艺术家ID：artistID=${artistID}"))
      artistOpt <- fetchArtist(artistID) // Fetch artist by artistID
      _ <- if (artistOpt.isEmpty)
        IO.raiseError(new IllegalStateException("艺术家ID不存在"))
      else
        IO(logger.info(s"艺术家(artistID=${artistID})已找到"))

      // Step 4: Check if the user is already in the managed_by list
      artist = artistOpt.get
      isAlreadyManager = artist.managedBy.exists(_.contains(userID))
      _ <- if (isAlreadyManager)
        IO.raiseError(new IllegalStateException("当前用户已与该艺术家绑定"))
      else
        IO(logger.info(s"用户(userID=${userID})尚未绑定艺术家，可以继续绑定"))

      // Step 5: Add userID to the managed_by list for the artist
      _ <- IO(logger.info(s"添加用户(userID=${userID})到艺术家的管理者列表中：artistID=${artistID}"))
      _ <- addManagerToArtist(artist, userID)
      _ <- IO(logger.info(s"用户(userID=${userID})成功绑定到艺术家(artistID=${artistID})"))

    } yield "更新成功" // Final return value
  }

  private def checkUserExists(userID: String)(using PlanContext): IO[Boolean] = {
    val sql =
      s"""
         |SELECT COUNT(*) FROM ${schemaName}.user
         |WHERE user_id = ?;
         |""".stripMargin
    readDBInt(sql, List(SqlParameter("String", userID))).map(_ > 0)
  }

  private def fetchArtist(artistID: String)(using PlanContext): IO[Option[Artist]] = {
    val sql =
      s"""
         |SELECT * FROM ${schemaName}.artist_table
         |WHERE artist_id = ?;
         |""".stripMargin
    readDBJsonOptional(sql, List(SqlParameter("String", artistID)))
      .map(_.map(decodeType[Artist]))
  }

  private def addManagerToArtist(artist: Artist, userID: String)(using PlanContext): IO[Unit] = {
    // Construct the updated managed_by list
    val updatedManagedBy = {
      val currentList = artist.managedBy.getOrElse(List.empty[String])
      currentList :+ userID
    }

    val sql =
      s"""
         |UPDATE ${schemaName}.artist_table
         |SET managed_by = ?
         |WHERE artist_id = ?;
         |""".stripMargin

    writeDB(
      sql,
      List(
        SqlParameter("String", updatedManagedBy.asJson.noSpaces),
        SqlParameter("String", artist.artistID)
      )
    ).flatMap { _ =>
      IO(logger.info(s"艺术家(artistID=${artist.artistID})的managed_by字段已更新"))
    }.void
  }
}