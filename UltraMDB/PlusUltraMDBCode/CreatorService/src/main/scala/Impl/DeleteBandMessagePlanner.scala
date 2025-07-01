package Impl

// External service APIs
import APIs.MusicService.FilterSongsByEntity
import APIs.OrganizeService.validateAdminMapping
// import APIs.TrackService.FilterAlbumsByEntity // Ideal future API

// Internal project common libraries
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName

// Third-party libraries and standard library
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._ // Assuming companion objects are now the standard

import org.slf4j.LoggerFactory

/**
 * Planner for DeleteBandMessage: Handles the deletion of a band.
 *
 * This action is restricted to administrators and is protected against deleting
 * bands that are still referenced by other entities (e.g., songs, albums).
 *
 * @param adminID    The ID of the administrator performing the action.
 * @param adminToken The administrator's authentication token.
 * @param bandID     The ID of the band to be deleted.
 * @param planContext The implicit execution context.
 */
case class DeleteBandMessagePlanner(
  adminID: String,
  adminToken: String,
  bandID: String,
  override val planContext: PlanContext
) extends Planner[(Boolean, String)] { // Corrected return type

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Boolean, String)] = {
    val logic: IO[(Boolean, String)] = for {
      // Step 1: Verify administrator credentials.
      _ <- verifyIsAdmin()

      // Step 2: Verify the band actually exists before proceeding.
      _ <- verifyBandExists()

      // Step 3: Ensure the (now confirmed to exist) band is not referenced.
      _ <- checkBandIsNotReferenced()

      // Step 4: Perform the final deletion.
      _ <- deleteBandFromDB()

    } yield (true, "乐队删除成功")

    logic.handleErrorWith { error =>
      logError(s"删除乐队 ${bandID} 的操作失败", error) >>
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

  private def verifyBandExists()(using PlanContext): IO[Unit] = {
    logInfo(s"正在确认乐队是否存在: ${bandID}")
    val sql = s"""SELECT 1 FROM "${schemaName}"."band_table" WHERE band_id = ?"""
    readDBRows(sql, List(SqlParameter("String", bandID))).flatMap {
      case Nil => IO.raiseError(new Exception("乐队ID不存在"))
      case _   => logInfo("乐队存在，继续执行。")
    }
  }

  private def checkBandIsNotReferenced()(using PlanContext): IO[Unit] = {
    logInfo(s"正在并行检查乐队 ${bandID} 是否被其他实体引用...")

    // Check 1: Is the band referenced in any songs?
    val songReferenceCheck: IO[Boolean] =
      FilterSongsByEntity(adminID, adminToken, entityID = Some(bandID), entityType = Some("band")).send.map {
        case (Some(songList), _) => songList.nonEmpty
        case (None, _)           => false
      }

    // Check 2: Is the band referenced in any albums? (Temporary direct query)
    val albumReferenceCheck: IO[Boolean] = {
      logInfo("执行对 album 表的直接引用检查 (待改进为API调用)")
      val bandIdAsJsonArray = s"""["$bandID"]"""
      val sql = s"""SELECT 1 FROM "${schemaName}"."album" WHERE creators::jsonb @> ?::jsonb OR collaborators::jsonb @> ?::jsonb LIMIT 1"""
      readDBRows(sql, List(SqlParameter("String", bandIdAsJsonArray))).map(_.nonEmpty)
    }

    // Run checks in parallel for efficiency.
    (songReferenceCheck, albumReferenceCheck).parTupled.flatMap {
      case (isReferencedInSongs, isReferencedInAlbums) =>
        val errors = List(
          if (isReferencedInSongs) Some("歌曲") else None,
          if (isReferencedInAlbums) Some("专辑") else None
        ).flatten

        if (errors.nonEmpty) {
          IO.raiseError(new Exception(s"无法删除：乐队已被 ${errors.mkString("、")} 引用"))
        } else {
          logInfo("引用检查通过，乐队未被引用。")
        }
    }
  }

  private def deleteBandFromDB()(using PlanContext): IO[Unit] = {
    logInfo(s"正在从数据库中删除乐队: ${bandID}")
    val sql = s"""DELETE FROM "${schemaName}"."band_table" WHERE band_id = ?"""
    writeDB(sql, List(SqlParameter("String", bandID))).void
  }

  private def logInfo(message: String): IO[Unit] = IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))
  private def logError(message: String, cause: Throwable): IO[Unit] = IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}