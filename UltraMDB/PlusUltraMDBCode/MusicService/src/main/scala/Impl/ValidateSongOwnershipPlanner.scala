package Impl


import Objects.CreatorService.Band
import Objects.CreatorService.Artist
import APIs.OrganizeService.validateAdminMapping
import APIs.OrganizeService.validateUserMapping
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import cats.effect.IO
import io.circe._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory
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
import APIs.OrganizeService.validateUserMapping
import io.circe.syntax._
import org.joda.time.DateTime
import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

case class ValidateSongOwnershipPlanner(
                                         userID: String,
                                         userToken: String,
                                         songID: String,
                                         override val planContext: PlanContext
                                       ) extends Planner[(Boolean, String)] {

  val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  override def plan(using PlanContext): IO[(Boolean, String)] = {
    (
      for {
        // Step 1: Validate user token
        (isUserValid,msg) <- validateUserMapping(userID, userToken).send
        _ <- IO(logger.info(s"用户令牌验证结果: ${isUserValid}"))
        _ <- if (!isUserValid)
          IO.raiseError(new IllegalArgumentException("Invalid user or token"))
        else IO.unit

        // Step 2: Check if the user is the uploader
        isUploader <- isUserUploader()
        _ <- IO(logger.info(s"是否为上传者: ${isUploader}"))

        // Step 3: Check if the user is creator or manager
        isCreatorManager <- if (!isUploader) isUserCreatorOrManager() else IO.pure(false)
        _ <- IO(logger.info(s"是否为创作者或管理者: ${isCreatorManager}"))

        // Step 4: Check if the user is an admin
        isAdmin <- if (!isUploader && !isCreatorManager) checkAdminPrivileges() else IO.pure(false)
        _ <- IO(logger.info(s"是否为管理员: ${isAdmin}"))

        // Step 5: Combine results
        isOwner = isUploader || isCreatorManager || isAdmin
        _ <- IO(logger.info(s"最终权限验证结果 isOwner = ${isOwner}"))

        _ <- if (!isOwner)
          IO.raiseError(new IllegalAccessException("用户无权操作该资源"))
        else IO.unit
      } yield (true, "") // 通过验证
      ).handleErrorWith { e =>
      IO(logger.error(s"权限验证失败: ${e.getMessage}")) *>
        IO.pure((false, e.getMessage)) // 验证失败，返回错误信息
    }
  }

  private def isUserUploader()(using PlanContext): IO[Boolean] = {
    for {
      _ <- IO(logger.info(s"开始验证是否为上传者"))
      uploaderOpt <- readDBJsonOptional(
        s"SELECT uploader_id FROM ${schemaName}.song_table WHERE song_id = ?",
        List(SqlParameter("String", songID))
      )
      uploaderID <- IO {
        uploaderOpt.map(json => decodeField[String](json, "uploader_id")).getOrElse("")
      }
      _ <- IO(logger.info(s"歌曲上传者ID: $uploaderID, 当前用户ID: $userID"))
    } yield uploaderID == userID
  }
  
  private def isUserCreatorOrManager()(using PlanContext): IO[Boolean] = {
    for {
      _ <- IO(logger.info(s"开始验证是否为创作者或管理者"))
      songDataOpt <- readDBJsonOptional(
        s"SELECT creators FROM ${schemaName}.song_table WHERE song_id = ?",
        List(SqlParameter("String", songID))
      )
      creatorsList <- IO {
        songDataOpt.map(json => decodeField[List[String]](json, "creators")).getOrElse(List())
      }
      _ <- IO(logger.info(s"歌曲创作者列表: ${creatorsList}"))

      isManagedByUser <- creatorsList.existsM(isManagedByUserID)
      _ <- IO(logger.info(s"是否存在创作者/乐队受用户 ${userID} 管理: ${isManagedByUser}"))
    } yield isManagedByUser
  }

  private def isManagedByUserID(creatorID: String)(using PlanContext): IO[Boolean] = {
    for {
      artistOpt <- readDBJsonOptional(
        s"SELECT managed_by FROM ${schemaName}.artist WHERE artist_id = ?",
        List(SqlParameter("String", creatorID))
      )
      bandOpt <- readDBJsonOptional(
        s"SELECT managed_by FROM ${schemaName}.band WHERE band_id = ?",
        List(SqlParameter("String", creatorID))
      )
      artistManagers <- IO {
        artistOpt.map(json => decodeField[List[String]](json, "managed_by")).getOrElse(List())
      }
      bandManagers <- IO {
        bandOpt.map(json => decodeField[List[String]](json, "managed_by")).getOrElse(List())
      }

      isManaged = artistManagers.contains(userID) || bandManagers.contains(userID)
    } yield isManaged
  }

  private def checkAdminPrivileges()(using PlanContext): IO[Boolean] = {
    for {
      (isAdmin,msg) <- validateAdminMapping(userID, userToken).send
    } yield isAdmin
  }
}