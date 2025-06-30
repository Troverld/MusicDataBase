package Impl


import APIs.OrganizeService.ValidateAdminMapping
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import cats.effect.IO
import org.slf4j.LoggerFactory
import io.circe.Json
import org.joda.time.DateTime
import cats.implicits.*
import io.circe.syntax.*
import io.circe.generic.auto._
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
import io.circe._
import io.circe.syntax._
import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

case class DeleteAlbumPlanner(
                               adminID: String,
                               adminToken: String,
                               albumID: String,
                               override val planContext: PlanContext
                             ) extends Planner[Boolean] {
  private val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  override def plan(using planContext: PlanContext): IO[Boolean] = {
    for {
      // Step 1: Validate adminToken and adminID
      _ <- IO(logger.info(s"[Step 1] 验证管理员是否具有删除权限"))
      isAdminValid <- ValidateAdmin(adminID, adminToken)
      _ <- if (isAdminValid) IO(logger.info(s"[Step 1.1] 管理员验证通过: adminID=${adminID}")) else IO.raiseError(new Exception("管理员验证失败"))

      // Step 2: Ensure albumID exists and is not referenced
      _ <- IO(logger.info(s"[Step 2] 检查专辑 albumID=${albumID} 是否存在以及是否被引用"))
      (albumExists, isReferenced) <- checkAlbumExistenceAndReferences(albumID)
      _ <- if (albumExists && !isReferenced) {
        IO(logger.info(s"[Step 2.1] 专辑 ${albumID} 存在且未被引用，可以删除"))
      } else {
        val reason =
          if (!albumExists) s"专辑 ${albumID} 不存在"
          else s"专辑 ${albumID} 已被引用，无法删除"
        IO(logger.warn(reason)) >> IO.raiseError(new Exception(reason))
      }

      // Step 3: Delete the album from AlbumTable
      _ <- IO(logger.info(s"[Step 3] 从 album_table 中删除专辑 ${albumID}"))
      deletionSuccessful <- deleteAlbum(albumID)

      // Step 4: Log the result
      _ <- if (deletionSuccessful) {
        IO(logger.info(s"[Step 4] 专辑删除成功: albumID=${albumID}"))
      } else {
        IO(logger.warn(s"[Step 4] 专辑删除失败: albumID=${albumID}"))
      }
    } yield deletionSuccessful
  }

  // Function to validate admin through ValidateAdminMapping API
  private def ValidateAdmin(adminID: String, adminToken: String)(using PlanContext): IO[Boolean] = {
    ValidateAdminMapping(adminID, adminToken).send.map {
      case true =>
        logger.info(s"管理员 adminID=${adminID} 验证通过")
        true
      case false =>
        logger.warn(s"管理员 adminID=${adminID} 验证失败")
        false
    }
  }

  // Function to check album existence and references
  private def checkAlbumExistenceAndReferences(albumID: String)(using PlanContext): IO[(Boolean, Boolean)] = {
    val albumExistenceSql =
      s"""
        SELECT 1
        FROM ${schemaName}.album_table
        WHERE album_id = ?;
      """
    val albumReferenceSql =
      s"""
        SELECT COUNT(*)
        FROM ${schemaName}.user_playlists
        WHERE album_id = ?;
      """

    for {
      _ <- IO(logger.info(s"检查 albumID=${albumID} 是否存在"))
      albumExists <- readDBJsonOptional(albumExistenceSql, List(SqlParameter("String", albumID))).map(_.isDefined)

      _ <- IO(logger.info(s"检查 albumID=${albumID} 是否被引用"))
      isReferenced <- readDBInt(albumReferenceSql, List(SqlParameter("String", albumID))).map(_ > 0)
    } yield (albumExists, isReferenced)
  }

  // Function to delete the album
  private def deleteAlbum(albumID: String)(using PlanContext): IO[Boolean] = {
    val sql =
      s"""
        DELETE FROM ${schemaName}.album_table
        WHERE album_id = ?;
      """
    writeDB(sql, List(SqlParameter("String", albumID))).attempt.map {
      case Right(_) =>
        logger.info(s"专辑 albumID=${albumID} 删除成功")
        true
      case Left(exception) =>
        logger.error(s"专辑 albumID=${albumID} 删除失败: ${exception.getMessage}")
        false
    }
  }
}