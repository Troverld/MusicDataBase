package Impl


import APIs.OrganizeService.ValidateAdminMapping
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import cats.effect.IO
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import io.circe.Json
import io.circe.syntax.*
import io.circe.generic.auto.*
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
import APIs.OrganizeService.ValidateAdminMapping
import io.circe.syntax._
import io.circe._
import io.circe.generic.auto._
import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

case class CreateArtistMessagePlanner(
                                       adminID: String,
                                       adminToken: String,
                                       name: String,
                                       bio: String,
                                       override val planContext: PlanContext
                                     ) extends Planner[String] {
  val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  override def plan(using PlanContext): IO[String] = {
    for {
      // Step 1: Verify adminToken and adminID mapping with ValidateAdminMapping API
      _ <- IO(logger.info("[Step 1] 验证管理员认证信息"))
      isValid <- ValidateAdminMapping(adminID, adminToken).send
      _ <- if (!isValid) {
        IO(logger.error("[Step 1] 管理员认证失败，验证失败"))
          .flatMap(_ => IO.raiseError(new IllegalArgumentException("管理员认证失败")))
      } else {
        IO(logger.info("[Step 1] 管理员认证成功"))
      }

      // Step 2: Check if the artist name is provided
      _ <- IO(logger.info("[Step 2] 检查艺术家名称是否提供"))
      _ <- checkArtistNameNonEmpty(name)

      // Step 3: Generate a unique artist ID
      _ <- IO(logger.info("[Step 3] 生成唯一的艺术家ID"))
      artistID <- generateUniqueArtistID()

      // Step 4: Insert the new artist into the ArtistTable
      _ <- IO(logger.info("[Step 4] 将新的艺术家记录插入数据库"))
      _ <- insertNewArtistRecord(artistID, name, bio)

      _ <- IO(logger.info(s"[Complete] 操作完成，返回生成的艺术家ID: ${artistID}"))
    } yield artistID
  }

  // Step 2.1: Check name is not empty
  private def checkArtistNameNonEmpty(name: String): IO[Unit] = {
    if (name.trim.isEmpty) {
      IO(logger.error("[Step 2.1] 艺术家名称不能为空"))
        .flatMap(_ => IO.raiseError(new IllegalArgumentException("艺术家名称不能为空")))
    } else {
      IO(logger.info(s"[Step 2.1] 艺术家名称验证通过：${name}"))
    }
  }

  // Step 3.1: Generate a unique artist ID
  private def generateUniqueArtistID()(using PlanContext): IO[String] = {
    val uniqueID = s"artist_${DateTime.now.getMillis}"
    IO(logger.info(s"[Step 3.1] 生成唯一艺术家ID: ${uniqueID}")) *> IO.pure(uniqueID)
  }

  // Step 4.1 & 4.2: Insert into ArtistTable
  private def insertNewArtistRecord(artistID: String, name: String, bio: String)(using PlanContext): IO[Unit] = {
    val sql =
      s"""
         |INSERT INTO ${schemaName}.artist_table (artist_id, name, bio, managed_by)
         |VALUES (?, ?, ?, ?);
         |""".stripMargin

    val parameters = List(
      SqlParameter("String", artistID),
      SqlParameter("String", name),
      SqlParameter("String", bio),
      SqlParameter("String", null) // Initialize managed_by field as NULL
    )

    IO(logger.info(s"[Step 4.1] 准备插入艺术家记录: artistID=${artistID}, name=${name}, bio=${bio}")) >>
      writeDB(sql, parameters).map { _ =>
        logger.info(s"[Step 4.2] 成功插入艺术家记录: artistID=${artistID}")
      }
  }
}