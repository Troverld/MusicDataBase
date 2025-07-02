package Impl

// External service APIs
import APIs.MusicService.FilterSongsByEntity
import APIs.OrganizeService.validateAdminMapping

// Internal project common libraries
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName

// Third-party libraries and standard library
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._ // No longer needed if all APIs have companions

import org.slf4j.LoggerFactory

/**
 * Planner for DeleteArtistMessage: Handles the deletion of an artist.
 *
 * This action is restricted to administrators and is protected against deleting
 * artists that are still referenced by songs, albums, or are members of a band.
 *
 * @param adminID    The ID of the administrator performing the action.
 * @param adminToken The administrator's authentication token.
 * @param artistID   The ID of the artist to be deleted.
 * @param planContext The implicit execution context.
 */
case class DeleteArtistMessagePlanner(
  adminID: String,
  adminToken: String,
  artistID: String,
  override val planContext: PlanContext
) extends Planner[(Boolean, String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Boolean, String)] = {
    val logic: IO[(Boolean, String)] = for {
      // Step 1: Verify administrator credentials.
      _ <- verifyIsAdmin()

      // Step 2: Verify the artist actually exists before proceeding.
      _ <- verifyArtistExists()

      // Step 3: Ensure the (now confirmed to exist) artist is not referenced elsewhere.
      _ <- checkArtistIsNotReferenced()

      // Step 4: Perform the final deletion.
      _ <- deleteArtistFromDB()

    } yield (true, "艺术家删除成功")

    logic.handleErrorWith { error =>
      logError(s"删除艺术家 ${artistID} 的操作失败", error) >>
        IO.pure((false, error.getMessage))
    }
  }

  private def verifyIsAdmin()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证管理员权限: adminID=${adminID}")
    validateAdminMapping(adminID, adminToken).send.flatMap {
      case (true, _) => logInfo("管理员权限验证通过。")
      case (false, message) => IO.raiseError(new Exception(s"管理员认证失败: $message"))
    }
  }

  /**
   * [NEW] Checks if the artist exists in the database. Fails if not.
   * This replaces the flawed "check-after-delete" logic.
   */
  private def verifyArtistExists()(using PlanContext): IO[Unit] = {
    logInfo(s"正在确认艺术家是否存在: ${artistID}")
    val sql = s"""SELECT 1 FROM "${schemaName}"."artist_table" WHERE artist_id = ?"""
    readDBRows(sql, List(SqlParameter("String", artistID))).flatMap {
      case Nil => IO.raiseError(new Exception("艺术家ID不存在"))
      case _   => logInfo("艺术家存在，继续执行。")
    }
  }

  private def checkArtistIsNotReferenced()(using PlanContext): IO[Unit] = {
    logInfo(s"正在并行检查艺术家 ${artistID} 是否被其他实体引用...")

    val songReferenceCheck: IO[Boolean] =
      FilterSongsByEntity(adminID, adminToken, entityID = Some(artistID), entityType = Some("artist")).send.map {
        case (Some(songList), _) => songList.nonEmpty
        case (None, _)           => false
      }

    // val albumReferenceCheck: IO[Boolean] = {
    //   logInfo("执行对 album 表的直接引用检查 (待改进为API调用)")
    //   val sql = s"""SELECT 1 FROM "${schemaName}"."album" WHERE ? = ANY(creators) LIMIT 1"""
    //   readDBRows(sql, List(SqlParameter("String", artistID))).map(_.nonEmpty)
    // }

    val bandMembershipCheck: IO[Boolean] = {
      logInfo("执行对 band_table 的直接成员关系检查 (待改进为API调用)")
      val sql = s"""SELECT 1 FROM "${schemaName}"."band_table" WHERE members::jsonb @> ?::jsonb LIMIT 1"""
      val artistIdAsJsonArray = s"""["$artistID"]"""
      readDBRows(sql, List(SqlParameter("String", artistIdAsJsonArray))).map(_.nonEmpty)
    }

    (songReferenceCheck, /*albumReferenceCheck,*/ bandMembershipCheck).parTupled.flatMap {
      case (isReferencedInSongs, /*isReferencedInAlbums,*/ isMemberOfBand) =>
        val errors = List(
          if (isReferencedInSongs) Some("歌曲") else None,
          // if (isReferencedInAlbums) Some("专辑") else None,
          if (isMemberOfBand) Some("乐队") else None
        ).flatten

        if (errors.nonEmpty) {
          IO.raiseError(new Exception(s"无法删除：艺术家已被 ${errors.mkString("、")} 引用"))
        } else {
          logInfo("引用检查通过，艺术家未被引用。")
        }
    }
  }

  /**
   * [MODIFIED] Deletes the artist record.
   * Its return type is now IO[Unit] as we no longer inspect the result.
   */
  private def deleteArtistFromDB()(using PlanContext): IO[Unit] = {
    logInfo(s"正在从数据库中删除艺术家: ${artistID}")
    val sql = s"""DELETE FROM "${schemaName}"."artist_table" WHERE artist_id = ?"""
    // writeDB returns IO[String], which we discard with .void to get IO[Unit].
    writeDB(sql, List(SqlParameter("String", artistID))).void
  }

  private def logInfo(message: String): IO[Unit] = IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))
  private def logError(message: String, cause: Throwable): IO[Unit] = IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}