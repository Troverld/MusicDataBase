package Impl


import APIs.OrganizeService.ValidateAdminMapping
import APIs.CreatorService.{validArtistOwnership, validBandOwnership}
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import org.joda.time.DateTime
import cats.effect.IO
import org.slf4j.LoggerFactory
import io.circe.syntax._
import io.circe.generic.auto._
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
import Objects.MusicService.Song
import APIs.CreatorService.validArtistOwnership
import APIs.CreatorService.validBandOwnership
import APIs.CreatorService.validBandOwnership
import io.circe.Json
import io.circe._
import cats.implicits.*
import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

case class CreateAlbumPlanner(
                                adminID: String,
                                adminToken: String,
                                name: String,
                                description: String,
                                releaseTime: DateTime,
                                creators: List[String],
                                collaborators: List[String],
                                contents: List[String],
                                override val planContext: PlanContext
                              ) extends Planner[String] {

  val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  override def plan(using PlanContext): IO[String] = {
    for {
      // Step 1: 验证管理员Token
      isAdminValid <- ValidateAdminMapping(adminID, adminToken).send
      _ <- if (!isAdminValid) {
        IO.raiseError(new IllegalAccessException(s"Admin ID $adminID and token validation failed"))
      } else {
        IO(logger.info(s"[Step 1]: Admin $adminID validated successfully"))
      }

      // Step 2: 验证专辑名称是否为空
      _ <- IO(logger.info(s"[Step 2]: Validating album name"))
      _ <- if (name.trim.isEmpty) {
        IO.raiseError(new IllegalArgumentException("[Error]: Album name cannot be empty"))
      } else {
        IO.unit
      }

      // Step 3: 验证creators和collaborators
      _ <- IO(logger.info("[Step 3]: Validating creators and collaborators"))
      _ <- validateArtistAndBandOwnership(creators, collaborators)

      // Step 4: 检查creators列表是否至少有一个有效ID
      _ <- IO(logger.info("[Step 4]: Validating whether creators list contains at least one valid ID"))
      _ <- if (creators.isEmpty) {
        IO.raiseError(new IllegalArgumentException("[Error]: Creators list must contain at least one valid artist ID"))
      } else {
        IO.unit
      }

      // Step 5: 验证contents列表中每个songID是否指向有效的歌曲
      _ <- IO(logger.info("[Step 5]: Validating contents (list of song IDs)"))
      validContents <- validateContents(contents)

      // Step 6: 生成唯一albumID并将数据存储到数据库
      albumID <- generateAndInsertAlbum(adminID, name, description, releaseTime, creators, collaborators, validContents)
    } yield albumID
  }

  // 验证creators（artists）和collaborators（bands）是否有效
  private def validateArtistAndBandOwnership(creators: List[String], collaborators: List[String])(using PlanContext): IO[Unit] = {
    for {
      // 验证creators（artists）
      _ <- IO(logger.info("[Step 3.1]: Validating artist ownership for creators"))
      creatorValidations <- creators.map(creatorID => validArtistOwnership(adminID, creatorID).send).sequence
      _ <- if (creatorValidations.contains(false)) {
        IO.raiseError(new IllegalArgumentException("[Error]: Some creator IDs are invalid"))
      } else {
        IO.unit
      }

      // 验证collaborators（bands）
      _ <- IO(logger.info("[Step 3.2]: Validating band ownership for collaborators"))
      collaboratorValidations <- collaborators.map(collaboratorID => validBandOwnership(adminID, collaboratorID).send).sequence
      _ <- if (collaboratorValidations.contains(false)) {
        IO.raiseError(new IllegalArgumentException("[Error]: Some collaborator IDs are invalid"))
      } else {
        IO.unit
      }
    } yield ()
  }

  // 验证歌曲contents列表中IDs的有效性
  private def validateContents(contents: List[String])(using PlanContext): IO[List[String]] = {
    contents.map { songID =>
      IO(logger.info(s"[Step 5.1]: Validating song ID: $songID")) *>
        readDBJsonOptional(
          s"SELECT * FROM ${schemaName}.song WHERE song_id = ?",
          List(SqlParameter("String", songID))
        ).flatMap {
          case Some(_) => IO.pure(songID) // 歌曲存在
          case None => IO.raiseError(new IllegalArgumentException(s"[Error]: Song ID $songID does not exist"))
        }
    }.sequence // 把所有验证过的songID收集为List
  }

  // 生成专辑ID并插入专辑数据到数据库
  private def generateAndInsertAlbum(
                                       adminID: String,
                                       name: String,
                                       description: String,
                                       releaseTime: DateTime,
                                       creators: List[String],
                                       collaborators: List[String],
                                       contents: List[String]
                                     )(using PlanContext): IO[String] = {
    for {
      albumID <- IO(java.util.UUID.randomUUID().toString) // Step 6.1: 生成唯一的albumID
      _ <- IO(logger.info(s"[Step 6.1]: Generated album ID: $albumID"))

      // Step 6.2: 插入专辑数据到数据库
      _ <- IO(logger.info(s"[Step 6.2]: Inserting album $albumID into database"))
      _ <- writeDB(
        s"""
           |INSERT INTO ${schemaName}.album_table
           |(album_id, name, creators, collaborators, release_time, description, contents)
           |VALUES (?, ?, ?, ?, ?, ?, ?);
           |""".stripMargin,
        List(
          SqlParameter("String", albumID),
          SqlParameter("String", name),
          SqlParameter("String", creators.asJson.noSpaces),
          SqlParameter("String", collaborators.asJson.noSpaces),
          SqlParameter("DateTime", releaseTime.getMillis.toString),
          SqlParameter("String", description),
          SqlParameter("String", contents.asJson.noSpaces)
        )
      )
    } yield albumID
  }
}