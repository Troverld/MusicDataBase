package Impl

// External service APIs
import APIs.CreatorService.GetArtistByID // <--- 关键导入
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
import org.slf4j.LoggerFactory

case class DeleteArtistMessagePlanner(
  adminID: String,
  adminToken: String,
  artistID: String,
  override val planContext: PlanContext
) extends Planner[(Boolean, String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Boolean, String)] = {
    val logic: IO[(Boolean, String)] = for {
      // Step 1: 验证管理员权限
      _ <- verifyIsAdmin()

      // Step 2: [已重构] 使用 API 验证艺术家是否存在
      _ <- verifyArtistExists()

      // Step 3: 检查艺术家引用关系 (此部分保持不变)
      _ <- checkArtistIsNotReferenced()

      // Step 4: 从数据库删除艺术家
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
   * 【已重构】
   * 使用 GetArtistByID API 检查艺术家是否存在。如果不存在则失败。
   */
  private def verifyArtistExists()(using PlanContext): IO[Unit] = {
    logInfo(s"正在通过 API 确认艺术家是否存在: ${artistID}")
    // 调用 GetArtistByID API
    GetArtistByID(adminID, adminToken, artistID).send.flatMap {
      // API 返回一个元组 (Option[Artist], String)
      case (Some(_), _) =>
        // 如果 Option[Artist] 是 Some，说明艺术家存在
        logInfo("艺术家存在，继续执行。")
      case (None, _) =>
        // 如果是 None，说明艺术家不存在，抛出错误
        IO.raiseError(new Exception("艺术家ID不存在"))
    }
  }

  /**
   * [保持不变]
   * 检查艺术家是否被其他实体引用
   */
  private def checkArtistIsNotReferenced()(using PlanContext): IO[Unit] = {
    logInfo(s"正在并行检查艺术家 ${artistID} 是否被其他实体引用...")

    val songReferenceCheck: IO[Boolean] =
      FilterSongsByEntity(adminID, adminToken, entityID = Some(artistID), entityType = Some("artist")).send.map {
        case (Some(songList), _) => songList.nonEmpty
        case _                   => false
      }

    val bandMembershipCheck: IO[Boolean] = {
      logInfo("执行对 band_table 的直接成员关系检查 (待改进为API调用)")
      val sql = s"""SELECT 1 FROM "${schemaName}"."band_table" WHERE members::jsonb @> ?::jsonb LIMIT 1"""
      val artistIdAsJsonArray = s"""["$artistID"]"""
      readDBRows(sql, List(SqlParameter("String", artistIdAsJsonArray))).map(_.nonEmpty)
    }

    (songReferenceCheck, bandMembershipCheck).parTupled.flatMap {
      case (isReferencedInSongs, isMemberOfBand) =>
        val errors = List(
          if (isReferencedInSongs) Some("歌曲") else None,
          if (isMemberOfBand) Some("乐队") else None
        ).flatten

        if (errors.nonEmpty) {
          IO.raiseError(new Exception(s"无法删除：艺术家已被 ${errors.mkString("、")} 引用"))
        } else {
          logInfo("引用检查通过，艺术家未被引用。")
        }
    }
  }

  private def deleteArtistFromDB()(using PlanContext): IO[Unit] = {
    logInfo(s"正在从数据库中删除艺术家: ${artistID}")
    val sql = s"""DELETE FROM "${schemaName}"."artist_table" WHERE artist_id = ?"""
    writeDB(sql, List(SqlParameter("String", artistID))).void
  }

  private def logInfo(message: String): IO[Unit] = IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))
  private def logError(message: String, cause: Throwable): IO[Unit] = IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}