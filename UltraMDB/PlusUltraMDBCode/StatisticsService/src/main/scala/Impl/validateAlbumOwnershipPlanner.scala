package Impl


import APIs.OrganizeService.validateAdminMapping
import APIs.OrganizeService.validateUserMapping
import APIs.CreatorService.validArtistOwnership
import APIs.CreatorService.validBandOwnership
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import cats.effect.IO
import org.slf4j.LoggerFactory
import io.circe.Json
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

case class ValidateAlbumOwnershipPlanner(
                                          userID: String,
                                          userToken: String,
                                          albumID: String,
                                          override val planContext: PlanContext
                                        ) extends Planner[Boolean] {
  val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  override def plan(using planContext: PlanContext): IO[Boolean] = {
    for {
      // Step 1: Validate user token
      _ <- IO(logger.info(s"正在验证用户令牌：userID=${userID}, userToken=${userToken}"))
      isUserValid <- validateUserMapping(userID, userToken).send
      _ <- IO.raiseWhen(!isUserValid)(new IllegalArgumentException(s"用户令牌无效或userID与userToken不匹配：${userID}"))
      
      // Step 2: Fetch album details
      _ <- IO(logger.info(s"通过albumID=${albumID}从数据库中检索专辑信息"))
      albumDetails <- fetchAlbumDetails(albumID)
      creators = decodeField[List[String]](albumDetails, "creators")
      collaborators = decodeField[List[String]](albumDetails, "collaborators")
      _ <- IO(logger.info(s"专辑创作者：$creators, 协作者：$collaborators"))

      // Step 3: Check if user has ownership in creators
      creatorOwnership <- checkOwnership(creators, "creators")
      _ <- IO(logger.info(s"用户是否在创作者中有所有权：${creatorOwnership}"))
      if creatorOwnership then IO.pure(true) else IO.unit

      // Step 4: Check if user has ownership in collaborators
      collaboratorOwnership <- checkOwnership(collaborators, "collaborators")
      _ <- IO(logger.info(s"用户是否在协作者中有所有权：${collaboratorOwnership}"))
      if collaboratorOwnership then IO.pure(true) else IO.unit

      // Step 5: Validate if user is an admin
      _ <- IO(logger.info(s"验证用户是否为管理员"))
      isAdmin <- validateAdminMapping(userID, userToken).send
      _ <- IO(logger.info(s"用户是否为管理员：$isAdmin"))
    } yield creatorOwnership || collaboratorOwnership || isAdmin
  }

  private def fetchAlbumDetails(albumID: String)(using PlanContext): IO[Json] = {
    val query =
      s"""
         |SELECT creators, collaborators
         |FROM ${schemaName}.album_table
         |WHERE album_id = ?;
       """.stripMargin
    readDBJson(query, List(SqlParameter("String", albumID)))
  }

  private def checkOwnership(list: List[String], listType: String)(using PlanContext): IO[Boolean] = {
    list.existsM { id =>
      for {
        isArtistOwner <- validArtistOwnership(userID, id).send
        isBandOwner <- validBandOwnership(userID, userToken, id).send
        hasOwnership = isArtistOwner || isBandOwner
        _ <- IO(logger.info(s"用户对${listType}中的ID=${id}是否有管理权限：ArtistOwner=$isArtistOwner, BandOwner=$isBandOwner"))
      } yield hasOwnership
    }
  }
}

// Extension Method for List.exists with IO effects
implicit class ListExistsM[T](list: List[T]) {
  def existsM(predicate: T => IO[Boolean])(using PlanContext): IO[Boolean] = {
    list.foldLeft(IO.pure(false)) { (accIO, elem) =>
      for {
        acc <- accIO
        found <- if (acc) IO.pure(true) else predicate(elem)
      } yield found
    }
  }
}