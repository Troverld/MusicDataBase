package Impl


import APIs.OrganizeService.ValidateAdminMapping
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import cats.effect.IO
import io.circe.Json
import org.slf4j.LoggerFactory
import cats.implicits._
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
import io.circe.generic.auto._
import org.joda.time.DateTime
import cats.implicits.*
import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

case class DeleteCollectionPlanner(
  adminID: String,
  adminToken: String,
  collectionID: String,
  override val planContext: PlanContext
) extends Planner[Boolean] {

  val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  // 覆盖 plan 方法，执行主要逻辑
  override def plan(using PlanContext): IO[Boolean] = {
    for {
      // Step 1: 验证管理员 Token 关联和有效性
      _ <- IO(logger.info(s"验证管理员(adminID=${adminID}, adminToken=${adminToken}) 的权限"))
      isAdminValid <- ValidateAdmin(accessKey = adminID, token = adminToken)
      _ <- if (!isAdminValid) IO.raiseError(new Exception(s"管理员(adminID=${adminID})验证失败，操作拒绝")) else IO.unit

      // Step 2: 检查歌单是否存在
      _ <- IO(logger.info(s"检查歌单(collectionID=${collectionID})是否存在"))
      collectionExists <- doesCollectionExist(collectionID)
      _ <- if (!collectionExists) IO.raiseError(new Exception(s"歌单(collectionID=${collectionID})不存在，无法删除")) else IO.unit

      // Step 3: 删除歌单记录
      _ <- IO(logger.info(s"删除歌单(collectionID=${collectionID})的记录"))
      isDeleted <- deleteCollection(collectionID)
      _ <- IO(logger.info(s"歌单(collectionID=${collectionID})删除结果: ${isDeleted}"))
    } yield isDeleted
  }

  // 验证管理员身份
  private def ValidateAdmin(accessKey: String, token: String)(using PlanContext): IO[Boolean] = {
    logger.info(s"调用 ValidateAdminMapping API 验证 accessKey=${accessKey} 和 token=${token} 的关联关系")
    ValidateAdminMapping(accessKey, token).send
  }

  // 检查歌单是否存在
  private def doesCollectionExist(collectionID: String)(using PlanContext): IO[Boolean] = {
    val sql =
      s"""
         |SELECT collection_id
         |FROM ${schemaName}.collection_table
         |WHERE collection_id = ?;
         """.stripMargin
    logger.info(s"开始创建检查歌单是否存在的数据库查询 SQL:\n${sql}")
    readDBJsonOptional(sql, List(SqlParameter("String", collectionID))).map {
      case Some(_) => true
      case None =>
        logger.warn(s"数据库查询未找到该歌单 collectionID=${collectionID}")
        false
    }
  }

  // 删除歌单
  private def deleteCollection(collectionID: String)(using PlanContext): IO[Boolean] = {
    val sql =
      s"""
         |DELETE FROM ${schemaName}.collection_table
         |WHERE collection_id = ?;
         """.stripMargin
    logger.info(s"开始创建删除歌单记录的数据库操作 SQL:\n${sql}")
    writeDB(sql, List(SqlParameter("String", collectionID))).map { _ =>
      logger.info(s"歌单 collectionID=${collectionID} 已成功删除")
      true
    }.handleErrorWith { error =>
      logger.error(s"删除歌单 collectionID=${collectionID} 失败，原因: ${error.getMessage}", error)
      IO(false)
    }
  }
}