package Impl


import APIs.OrganizeService.validateAdminMapping
import Objects.MusicService.Song
import Objects.TrackService.Album
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import cats.effect.IO
import org.slf4j.LoggerFactory
import io.circe.Json
import org.joda.time.DateTime
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
import Objects.TrackService.Album
import io.circe.parser._
import io.circe._
import io.circe.syntax._
import io.circe.generic.auto._
import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

case class DeleteBandMessagePlanner(
                                     adminID: String,
                                     adminToken: String,
                                     bandID: String,
                                     override val planContext: PlanContext
                                   ) extends Planner[String] {
  val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  override def plan(using planContext: PlanContext): IO[String] = {
    for {
      // Step 1: Validate adminToken and ID, and admin privileges
      _ <- IO(logger.info("验证管理员权限"))
      isValidAdmin <- validateAdminMapping(adminID, adminToken).send
      _ <- if (!isValidAdmin) IO.raiseError(new IllegalArgumentException("管理员认证失败")) else IO(logger.info("管理员验证成功"))

      // Step 2: Check if the bandID exists in the BandTable
      _ <- IO(logger.info(s"检查乐队ID '${bandID}' 是否存在"))
      bandExists <- checkBandExists(bandID)
      _ <- if (!bandExists) IO.raiseError(new IllegalArgumentException("乐队ID不存在")) else IO(logger.info("乐队存在验证通过"))

      // Step 3: Verify if the bandID is referenced in Songs or Albums
      _ <- IO(logger.info(s"检查乐队ID '${bandID}' 是否有引用"))
      isReferenced <- checkIfBandIsReferenced(bandID)
      _ <- if (isReferenced) IO.raiseError(new IllegalArgumentException("乐队已被引用，无法删除")) else IO(logger.info("乐队无引用验证通过"))

      // Step 4: Delete the band from the BandTable
      _ <- IO(logger.info(s"从数据库中删除乐队ID '${bandID}'"))
      deletionResult <- deleteBand(bandID)
      _ <- IO(logger.info(s"删除操作成功: $deletionResult"))

    } yield "删除成功"
  }

  // Helper function: Check if the bandID exists in the BandTable
  private def checkBandExists(bandID: String)(using PlanContext): IO[Boolean] = {
    for {
      _ <- IO(logger.info(s"创建检查乐队ID '${bandID}' 的数据库查询语句"))
      sql <- IO {
        s"""
          SELECT 1
          FROM ${schemaName}.band_table
          WHERE band_id = ?;
        """
      }
      result <- readDBJsonOptional(sql, List(SqlParameter("String", bandID))).map(_.isDefined)
      _ <- IO(logger.info(s"检查乐队ID '${bandID}' 结果: ${result}"))
    } yield result
  }

  // Helper function: Check if the bandID is referenced in Song/Album tables
  private def checkIfBandIsReferenced(bandID: String)(using PlanContext): IO[Boolean] = {
    val bandIdJson = s"""["${bandID}"]""" // JSON array format

    for {
      _ <- IO(logger.info(s"检查乐队ID '${bandID}' 是否被Song表引用"))
      songSql <- IO {
        s"""
          SELECT 1
          FROM ${schemaName}.song
          WHERE performers @> ?::jsonb OR creators @> ?::jsonb;
        """
      }
      referencedInSongs <- readDBJsonOptional(
        songSql,
        List(
          SqlParameter("String", bandIdJson),
          SqlParameter("String", bandIdJson)
        )
      ).map(_.isDefined)

      _ <- IO(logger.info(s"检查乐队ID '${bandID}' 是否被Album表引用"))
      albumSql <- IO {
        s"""
          SELECT 1
          FROM ${schemaName}.album
          WHERE creators @> ?::jsonb OR collaborators @> ?::jsonb;
        """
      }
      referencedInAlbums <- readDBJsonOptional(
        albumSql,
        List(
          SqlParameter("String", bandIdJson),
          SqlParameter("String", bandIdJson)
        )
      ).map(_.isDefined)

      result <- IO {
        referencedInSongs || referencedInAlbums
      }
      _ <- IO(logger.info(s"乐队是否被引用 (Songs: ${referencedInSongs}, Albums: ${referencedInAlbums}): ${result}"))
    } yield result
  }

  // Helper function: Delete the band record from the BandTable
  private def deleteBand(bandID: String)(using PlanContext): IO[String] = {
    for {
      _ <- IO(logger.info(s"创建删除乐队ID '${bandID}' 的SQL语句"))
      sql <- IO {
        s"""
          DELETE FROM ${schemaName}.band_table
          WHERE band_id = ?;
        """
      }
      result <- writeDB(sql, List(SqlParameter("String", bandID)))
      _ <- IO(logger.info(s"删除乐队记录结果: $result"))
    } yield result
  }
}