package Impl


import APIs.OrganizeService.{validateAdminMapping, validateUserMapping}
import Objects.TrackService.Collection
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
import Common.Serialize.CustomColumnTypes.{decodeDateTime, encodeDateTime}
import cats.implicits._
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
import APIs.OrganizeService.validateUserMapping
import Objects.TrackService.Collection
import io.circe._
import cats.implicits.*
import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

case class ValidateCollectionOwnershipPlanner(
    userID: String,
    userToken: String,
    collectionID: String,
    override val planContext: PlanContext
) extends Planner[Boolean] {
  val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  override def plan(using PlanContext): IO[Boolean] = {
    for {
      // Step 1: 验证用户令牌是否有效
      _ <- IO(logger.info(s"[Step 1] 开始验证用户令牌: userID=${userID}, userToken=${userToken}"))
      isTokenValid <- validateUserToken()
      _ <- if (isTokenValid) IO(logger.info("[Step 1.1] 用户令牌验证通过"))
           else IO(logger.error("[Step 1.1] 用户令牌验证失败")) >>
             IO.raiseError(new RuntimeException(s"Invalid user token for userID=${userID}"))

      // Step 2: 检索 collectionID 的相关记录
      _ <- IO(logger.info(s"[Step 2] 从CollectionTable中检索歌单记录: collectionID=${collectionID}"))
      maybeCollectionRecord <- getCollectionRecord()
      collection <- maybeCollectionRecord match {
        case Some(record) => IO(logger.info("[Step 2.1] 歌单记录存在")) >> IO(record)
        case None =>
          val errorMessage = s"[Step 2.1] 歌单记录不存在，collectionID=${collectionID}"
          IO(logger.error(errorMessage)) >> IO.raiseError(new RuntimeException(errorMessage))
      }

      // Step 3: 检查用户是否为歌单的所有者
      _ <- IO(logger.info(s"[Step 3] 检查用户是否为歌单所有者: userID=${userID}, ownerID=${collection.ownerID}"))
      isOwner = userID == collection.ownerID
      _ <- IO(logger.info(if (isOwner) "[Step 3.1] 用户是歌单所有者" else "[Step 3.1] 用户不是歌单所有者"))

      // Step 4: 检查用户是否为歌单的维护者
      _ <- IO(logger.info(s"[Step 4] 检查用户是否为歌单的维护者: userID=${userID}, maintainers=${collection.maintainers}"))
      isMaintainer = collection.maintainers.contains(userID)
      _ <- IO(logger.info(if (isMaintainer) "[Step 4.1] 用户是歌单维护者" else "[Step 4.1] 用户不是歌单维护者"))

      // Step 5: 检查用户是否为管理员
      _ <- IO(logger.info(s"[Step 5] 检查用户是否为管理员: userID=${userID}, userToken=${userToken}"))
      isAdmin <- validateAdminToken()
      _ <- IO(logger.info(if (isAdmin) "[Step 5.1] 用户是管理员" else "[Step 5.1] 用户不是管理员"))

    } yield isOwner || isMaintainer || isAdmin
  }

  /** 验证用户令牌是否合法 */
  private def validateUserToken()(using PlanContext): IO[Boolean] = {
    validateUserMapping(userID, userToken).send
  }

  /** 检索歌单记录，并解析为Collection对象 */
  private def getCollectionRecord()(using PlanContext): IO[Option[Collection]] = {
    val sql =
      s"""
         |SELECT * FROM ${schemaName}.collection_table WHERE collection_id = ?;
         |""".stripMargin
    readDBJsonOptional(sql, List(SqlParameter("String", collectionID)))
      .map(_.map(decodeType[Collection]))
  }

  /** 验证用户是否是管理员 */
  private def validateAdminToken()(using PlanContext): IO[Boolean] = {
    validateAdminMapping(userID, userToken).send
  }
}