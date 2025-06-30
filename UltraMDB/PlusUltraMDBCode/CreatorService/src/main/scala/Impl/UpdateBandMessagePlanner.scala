package Impl


import APIs.OrganizeService.ValidateUserMapping
import APIs.CreatorService.validArtistOwnership
import APIs.CreatorService.validBandOwnership
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import cats.effect.IO
import io.circe.Json
import org.slf4j.LoggerFactory
import io.circe.syntax._
import io.circe.generic.auto._
import org.joda.time.DateTime
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
import APIs.CreatorService.validBandOwnership
import io.circe._
import cats.implicits.*
import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

case class UpdateBandMessagePlanner(
                                     userID: String,
                                     userToken: String,
                                     bandID: String,
                                     name: Option[String],
                                     members: List[String],
                                     bio: Option[String],
                                     override val planContext: PlanContext
                                   ) extends Planner[String] {
  val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  override def plan(using PlanContext): IO[String] = {
    for {
      // Step 1: Validate user token and userID mapping
      _ <- IO(logger.info(s"[Step 1] 验证用户令牌和用户ID映射关系，userID=$userID"))
      isUserValid <- ValidateUserMapping(userID, userToken).send
      _ <- if (!isUserValid) IO.raiseError(new Exception("用户令牌无效")) else IO(logger.info("[Step 1.1] 用户令牌验证成功"))

      // Step 2: Check if the user has the ownership of the band
      _ <- IO(logger.info(s"[Step 2] 验证用户对乐队的管理权限，bandID=$bandID"))
      isBandOwner <- validBandOwnership(userID, userToken, bandID).send
      _ <- if (!isBandOwner) IO.raiseError(new Exception("无权限修改该乐队信息")) else IO(logger.info("[Step 2.1] 用户对乐队的管理权限验证通过"))

      // Step 3: Check if the band exists in the database
      _ <- IO(logger.info(s"[Step 3] 检查乐队是否存在，bandID=$bandID"))
      bandExists <- validateBandExistence(bandID)
      _ <- if (!bandExists) IO.raiseError(new Exception("乐队ID不存在")) else IO(logger.info("[Step 3.1] 乐队存在"))

      // Step 4: Update band information
      _ <- IO(logger.info("[Step 4] 开始更新乐队信息"))
      _ <- updateBandInfo()

    } yield "更新成功"
  }

  private def validateBandExistence(bandID: String)(using PlanContext): IO[Boolean] = {
    IO(logger.info(s"[validateBandExistence] 查询band_table是否有对应乐队ID: $bandID")) >>
      readDBInt(
        s"""
          |SELECT COUNT(1)
          |FROM ${schemaName}.band_table
          |WHERE band_id = ?;
        """.stripMargin,
        List(SqlParameter("String", bandID))
      ).map(_ > 0)
  }

  private def updateBandInfo()(using PlanContext): IO[Unit] = {
    val updates = scala.collection.mutable.ArrayBuffer.empty[String]
    val sqlParams = scala.collection.mutable.ArrayBuffer.empty[SqlParameter]

    if (name.isDefined) {
      updates += "name = ?"
      sqlParams += SqlParameter("String", name.get)
      logger.info(s"[updateBandInfo] 将name更新为：${name.get}")
    }

    if (bio.isDefined) {
      updates += "bio = ?"
      sqlParams += SqlParameter("String", bio.get)
      logger.info(s"[updateBandInfo] 将bio更新为：${bio.get}")
    }

    if (members.nonEmpty) {
      logger.info("[updateBandInfo] 开始验证每个成员ID的有效性")
      val memberValidityChecks = members.map(memberID => validArtistOwnership(userID, memberID).send)

      for {
        memberValidity <- memberValidityChecks.sequence
        _ <- if (memberValidity.contains(false)) IO.raiseError(new Exception("部分成员ID无效")) else IO(logger.info("[updateBandInfo] 所有成员ID有效"))
      } yield ()

      updates += "members = ?"
      sqlParams += SqlParameter("String", members.asJson.noSpaces) // JSON格式存储
      logger.info(s"[updateBandInfo] 将members更新为：${members.mkString("[", ", ", "]")}")
    }

    if (updates.isEmpty) {
      IO(logger.info("[updateBandInfo] 更新操作跳过，因为没有需要更新的字段"))
    } else {
      val sql =
        s"""
          |UPDATE ${schemaName}.band_table
          |SET ${updates.mkString(", ")}
          |WHERE band_id = ?;
        """.stripMargin

      sqlParams += SqlParameter("String", bandID)
      writeDB(sql, sqlParams.toList).map(_ => logger.info("[updateBandInfo] 乐队信息更新成功"))
    }
  }
}