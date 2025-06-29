package Impl


import APIs.OrganizeService.validateUserMapping
import APIs.CreatorService.validArtistOwnership
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import cats.effect.IO
import org.slf4j.LoggerFactory
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
import APIs.CreatorService.validArtistOwnership
import io.circe.Json
import io.circe.syntax._
import org.joda.time.DateTime
import io.circe._
import io.circe.generic.auto._
import cats.implicits.*
import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

case class UpdateArtistMessagePlanner(
    userID: String,
    userToken: String,
    artistID: String,
    name: Option[String],
    bio: Option[String],
    override val planContext: PlanContext
) extends Planner[String] {

  private val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  override def plan(using PlanContext): IO[String] = for {
    // Step 1: Validate userToken and userID mapping
    _ <- IO(logger.info(s"[Step 1] 验证用户映射: userID=${userID}, userToken=${userToken}"))
    isValidUser <- validateUserMapping(userID, userToken).send
    _ <- if (!isValidUser) IO.raiseError(new IllegalArgumentException("用户验证失败"))
         else IO(logger.info("[Step 1.1] 用户验证成功"))

    // Step 2: Validate user ownership of artistID
    _ <- IO(logger.info(s"[Step 2] 验证用户管理艺术家权限: userID=${userID}, artistID=${artistID}"))
    isOwner <- validArtistOwnership(userID, artistID).send
    _ <- if (!isOwner) IO.raiseError(new IllegalArgumentException("您无权限修改该艺术家信息"))
         else IO(logger.info("[Step 2.1] 用户拥有管理权限"))

    // Step 3: Check if artistID exists in the database
    _ <- IO(logger.info(s"[Step 3] 验证艺术家ID是否存在: artistID=${artistID}"))
    artistExists <- checkArtistExists(artistID)
    _ <- if (!artistExists) IO.raiseError(new IllegalArgumentException("艺术家ID不存在"))
         else IO(logger.info("[Step 3.1] 艺术家ID验证通过"))

    // Step 4: Update artist information if applicable
    _ <- IO(logger.info(s"[Step 4] 尝试更新艺术家信息: name=${name.getOrElse("未提供")}, bio=${bio.getOrElse("未提供")}"))
    updateResult <- updateArtistInfo(artistID, name, bio)
    _ <- IO(logger.info(s"[Step 4.1] 艺术家信息更新操作完成，结果: ${updateResult}"))

  } yield "更新成功"

  // Step 3.1: Check if the artistID exists in the database
  private def checkArtistExists(artistID: String)(using PlanContext): IO[Boolean] = {
    val sql =
      s"SELECT COUNT(*) FROM ${schemaName}.artist_table WHERE artist_id = ?;"
    val parameters = List(SqlParameter("String", artistID))

    IO(logger.info(s"[Step 3.1] 检测艺术家是否存在的SQL: ${sql}，参数: ${parameters}")) >>
      readDBInt(sql, parameters).map { count =>
        logger.info(s"[Step 3.2] 检测结果: ${count > 0}")
        count > 0
      }
  }

  // Step 4.x: Update artist information in the database
  private def updateArtistInfo(artistID: String, name: Option[String], bio: Option[String])(using PlanContext): IO[String] = {
    val updates = buildUpdateSql(name, bio)
    if (updates.isEmpty) {
      logger.info("[Step 4.x] 没有需要更新的字段，略过更新步骤")
      IO.pure("没有更新内容")
    } else {
      val sql = s"UPDATE ${schemaName}.artist_table SET ${updates.mkString(", ")} WHERE artist_id = ?;"
      val parameters = buildSqlParameters(name, bio, artistID)
      IO(logger.info(s"[Step 4.x] 更新艺术家信息的SQL: ${sql}，参数: ${parameters}")) >>
        writeDB(sql, parameters).map(result => {
          logger.info(s"[Step 4.x] 数据库操作结果: ${result}")
          result
        })
    }
  }

  // Helper function to construct SQL update string
  private def buildUpdateSql(name: Option[String], bio: Option[String]): List[String] = {
    var updates = List.empty[String]
    name.foreach(_ => updates = updates :+ "name = ?")
    bio.foreach(_ => updates = updates :+ "bio = ?")
    updates
  }

  // Helper function to construct SQL parameters
  private def buildSqlParameters(name: Option[String], bio: Option[String], artistID: String): List[SqlParameter] = {
    val nameParam = name.map(n => SqlParameter("String", n))
    val bioParam = bio.map(b => SqlParameter("String", b))
    (nameParam.toList ++ bioParam.toList) :+ SqlParameter("String", artistID)
  }
}