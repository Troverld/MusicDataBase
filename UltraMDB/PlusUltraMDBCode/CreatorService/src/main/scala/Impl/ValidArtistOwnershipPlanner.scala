package Impl


import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import cats.effect.IO
import org.slf4j.LoggerFactory
import io.circe.Json
import io.circe.syntax._
import io.circe.generic.auto._
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

import io.circe._
import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

case class ValidArtistOwnershipPlanner(
                                        userID: String,
                                        artistID: String,
                                        override val planContext: PlanContext
                                      ) extends Planner[Boolean] {
  val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  override def plan(using PlanContext): IO[Boolean] = {
    for {
      // Step 1: Fetch managed_by field from the ArtistTable based on artistID
      _ <- IO(logger.info(s"开始验证用户是否拥有管理艺术家[artistID=${artistID}]的权限"))
      managedBy <- getManagedBy(artistID)

      // Step 2: Check if managedBy contains the userID
      _ <- IO(logger.info(s"检查managed_by字段是否包含用户[userID=${userID}]"))
      isOwner <- IO(managedBy.contains(userID))
      _ <- IO(logger.info(s"验证结果为isOwner=${isOwner}"))
    } yield isOwner
  }

  private def getManagedBy(artistID: String)(using PlanContext): IO[List[String]] = {
    for {
      _ <- IO(logger.info(s"从数据库读取artistID=${artistID}的managed_by字段"))
      sql <- IO {
        s"""
         SELECT managed_by
         FROM ${schemaName}.artist_table
         WHERE artist_id = ?;
       """
      }
      parameters <- IO { List(SqlParameter("String", artistID)) }
      _ <- IO(logger.info(s"SQL查询语句: ${sql}"))
      managedByRawOpt <- readDBJsonOptional(sql, parameters)
      managedBy <- managedByRawOpt match {
        case Some(json) =>
          for {
            managedByRaw <- IO(decodeField[String](json, "managed_by"))
            _ <- IO(logger.info(s"获取到的managed_by字段: ${managedByRaw}"))
            managedByList <- IO(decodeType[List[String]](managedByRaw))
          } yield managedByList

        case None =>
          for {
            _ <- IO(logger.warn(s"未查询到artistID=${artistID}对应的记录，返回空列表"))
          } yield List.empty[String]
      }
    } yield managedBy
  }
}
