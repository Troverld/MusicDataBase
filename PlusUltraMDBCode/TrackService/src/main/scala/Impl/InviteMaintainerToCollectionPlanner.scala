package Impl


import APIs.OrganizeService.validateUserMapping
import APIs.TrackService.validateCollectionOwnership
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import cats.effect.IO
import org.slf4j.LoggerFactory
import io.circe._
import io.circe.syntax._
import io.circe.generic.auto._
import cats.implicits.*
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
import APIs.TrackService.validateCollectionOwnership
import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

case class InviteMaintainerToCollectionPlanner(
  collectionID: String,
  userID: String,
  userToken: String,
  invitedUserID: String,
  override val planContext: PlanContext
) extends Planner[Boolean] {
  val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  override def plan(using planContext: PlanContext): IO[Boolean] = {
    for {
      // Step 1: 验证用户令牌与用户ID的关联关系
      _ <- IO(logger.info(s"验证用户令牌 ${userToken} 与用户ID ${userID} 的关联关系"))
      isUserValid <- validateUserMapping(userID, userToken).send
      _ <- if (!isUserValid) {
        IO(logger.error("用户令牌与用户ID的关联关系验证失败")) >>
          IO.pure(false)
      } else IO.unit

      // Step 2: 验证用户是否为歌单的所有者或维护者
      _ <- IO(logger.info(s"验证用户 ${userID} 是否为歌单 ${collectionID} 的所有者或维护者"))
      hasCollectionPermission <- validateCollectionOwnership(userID, userToken, collectionID).send
      _ <- if (!hasCollectionPermission) {
        IO(logger.error("验证失败，用户没有对当前歌单的权限")) >>
          IO.pure(false)
      } else IO.unit

      // Step 3: 检查 invitedUserID 是否合法
      _ <- IO(logger.info(s"检查被邀请用户 ${invitedUserID} 是否存在"))
      isInvitedUserValid <- validateUserMapping(invitedUserID, "").send
      _ <- if (!isInvitedUserValid) {
        IO(logger.error("被邀请人不存在")) >>
          IO.pure(false)
      } else IO.unit

      // Step 4: 检查 invitedUserID 是否已经是维护者
      _ <- IO(logger.info(s"检查用户 ${invitedUserID} 是否已经是歌单 ${collectionID} 的维护者"))
      existingMaintainersOpt <- getMaintainersForCollection(collectionID)
      isAlreadyMaintainer = existingMaintainersOpt.exists(_.contains(invitedUserID))
      _ <- if (isAlreadyMaintainer) {
        IO(logger.error("被邀请人已是维护者")) >>
          IO.pure(false)
      } else IO.unit

      // Step 5: 将 invitedUserID 添加到维护者列表中
      _ <- IO(logger.info(s"用户 ${invitedUserID} 被添加为歌单 ${collectionID} 的维护者"))
      newMaintainersList = addInvitedUserToMaintainers(existingMaintainersOpt, invitedUserID)
      _ <- updateMaintainersForCollection(collectionID, newMaintainersList)

      _ <- IO(logger.info("邀请成功，操作完成"))
    } yield true
  }.handleErrorWith { e =>
    logger.error(s"操作失败: ${e.getMessage}")
    IO.pure(false)
  }

  /** 获取歌单的维护者列表 */
  private def getMaintainersForCollection(collectionID: String)(using PlanContext): IO[Option[List[String]]] = {
    val sql = s"SELECT maintainers FROM ${schemaName}.collection_table WHERE collection_id = ?"
    readDBJsonOptional(sql, List(SqlParameter("String", collectionID))).map {
      case Some(json) =>
        val maintainersJson = decodeField[String](json, "maintainers") // maintainers 字段是 JSON 格式
        Some(decodeType[List[String]](maintainersJson))
      case None =>
        None
    }
  }

  /** 将被邀请用户添加到现有维护者列表 **/
  private def addInvitedUserToMaintainers(existingMaintainersOpt: Option[List[String]], invitedUserID: String): List[String] = {
    existingMaintainersOpt match {
      case Some(maintainers) => maintainers :+ invitedUserID
      case None              => List(invitedUserID)
    }
  }

  /** 更新歌单的维护者列表 */
  private def updateMaintainersForCollection(collectionID: String, maintainersList: List[String])(using PlanContext): IO[String] = {
    val maintainersJson = maintainersList.asJson.noSpaces
    val sql =
      s"""
        UPDATE ${schemaName}.collection_table
        SET maintainers = ?
        WHERE collection_id = ?
      """
    writeDB(
      sql,
      List(
        SqlParameter("String", maintainersJson),
        SqlParameter("String", collectionID)
      )
    )
  }
}