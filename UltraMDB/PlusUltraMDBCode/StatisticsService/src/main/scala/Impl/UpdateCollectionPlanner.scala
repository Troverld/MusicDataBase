package Impl


/**
 * Planner for UpdateCollection: 用于通过 collectionID 修改歌单信息
 * 功能: 更新歌单名称、简介、内容、维护者等内容, 用于对用户创建的歌单进行更新操作
 */
import APIs.TrackService.validateCollectionOwnership
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import cats.effect.IO
import org.slf4j.LoggerFactory
import io.circe.syntax._
import io.circe.Json
import io.circe._
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
import APIs.TrackService.validateCollectionOwnership
import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

case class UpdateCollectionPlanner(
    collectionID: String,
    name: Option[String],
    description: Option[String],
    contents: List[String],
    maintainers: List[String],
    userID: String,
    userToken: String,
    override val planContext: PlanContext
) extends Planner[Boolean] {

  private val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  override def plan(using planContext: PlanContext): IO[Boolean] = {
    for {
      // Step 1: Validate collection ownership
      _ <- IO(logger.info("[Step 1] 开始验证用户对歌单的权限"))
      hasPermission <- validateCollectionOwnership(userID, userToken, collectionID).send.handleErrorWith { ex =>
        IO(logger.error(s"[Step 1] 权限验证失败, 错误原因: ${ex.getMessage}")).as(false)
      }
      _ <- IO(logger.info(s"[Step 1] 用户权限验证结果: hasPermission=${hasPermission}"))

      // 如果用户无权限，直接返回 false，不抛出异常
      isAuthorized <- if (!hasPermission) {
        IO(logger.warn("[Step 1] 用户无权限修改歌单")) >> IO(false)
      } else {
        IO(true)
      }

      updatedSuccess <-
        if (isAuthorized) {
          for {
            // Step 2: 按需更新各字段
            _ <- IO(logger.info("[Step 2] 开始按需更新歌单信息"))
            _ <- updateCollectionNameIfProvided
            _ <- updateCollectionDescriptionIfProvided
            _ <- updateCollectionContentsIfProvided
            _ <- updateCollectionMaintainersIfProvided

            // Step 3: 更新完成，返回 success=true
            _ <- IO(logger.info(s"[Step 3] 歌单信息更新成功, collectionID=${collectionID}"))
          } yield true
        } else {
          IO(false) // 返回 false，表示失败
        }
    } yield updatedSuccess
  }

  private def updateCollectionNameIfProvided()(using PlanContext): IO[Unit] = {
    name match {
      case Some(value) =>
        for {
          _ <- IO(logger.info("[Step 2.1] 开始更新歌单名称"))
          sql <- IO(
            s"UPDATE ${schemaName}.collection_table SET name = ? WHERE collection_id = ?"
          )
          _ <- writeDB(
            sql,
            List(
              SqlParameter("String", value),
              SqlParameter("String", collectionID)
            )
          ).map(_ => logger.info(s"[Step 2.1] 成功更新歌单名称为: ${value}"))
        } yield ()
      case None => IO(logger.info("[Step 2.1] 未提供歌单名称，跳过更新"))
    }
  }

  private def updateCollectionDescriptionIfProvided()(using PlanContext): IO[Unit] = {
    description match {
      case Some(value) =>
        for {
          _ <- IO(logger.info("[Step 2.2] 开始更新歌单简介"))
          sql <- IO(
            s"UPDATE ${schemaName}.collection_table SET description = ? WHERE collection_id = ?"
          )
          _ <- writeDB(
            sql,
            List(
              SqlParameter("String", value),
              SqlParameter("String", collectionID)
            )
          ).map(_ => logger.info(s"[Step 2.2] 成功更新歌单简介为: ${value}"))
        } yield ()
      case None => IO(logger.info("[Step 2.2] 未提供歌单简介，跳过更新"))
    }
  }

  private def updateCollectionContentsIfProvided()(using PlanContext): IO[Unit] = {
    if (contents.nonEmpty) {
      for {
        _ <- IO(logger.info("[Step 2.3] 开始更新歌单内容"))
        contentsJson <- IO(contents.asJson.noSpaces) // 转换为 JSON 格式
        sql <- IO(
          s"UPDATE ${schemaName}.collection_table SET contents = ? WHERE collection_id = ?"
        )
        _ <- writeDB(
          sql,
          List(
            SqlParameter("String", contentsJson),
            SqlParameter("String", collectionID)
          )
        ).map(_ => logger.info(s"[Step 2.3] 成功更新歌单内容"))
      } yield ()
    } else {
      IO(logger.info("[Step 2.3] 未更新歌单内容，跳过"))
    }
  }

  private def updateCollectionMaintainersIfProvided()(using PlanContext): IO[Unit] = {
    if (maintainers.nonEmpty) {
      for {
        _ <- IO(logger.info("[Step 2.4] 开始更新歌单维护者"))
        maintainersJson <- IO(maintainers.asJson.noSpaces) // 转换为 JSON 格式
        sql <- IO(
          s"UPDATE ${schemaName}.collection_table SET maintainers = ? WHERE collection_id = ?"
        )
        _ <- writeDB(
          sql,
          List(
            SqlParameter("String", maintainersJson),
            SqlParameter("String", collectionID)
          )
        ).map(_ => logger.info(s"[Step 2.4] 成功更新歌单维护者"))
      } yield ()
    } else {
      IO(logger.info("[Step 2.4] 未提供歌单维护者，跳过更新"))
    }
  }
}