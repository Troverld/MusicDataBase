package Impl

// External service APIs
import Utils.SearchUtil
import APIs.MusicService.FilterSongsByEntity
import APIs.OrganizeService.validateAdminMapping
import Objects.CreatorService.{CreatorID_Type, CreatorType}

// Internal project common libraries
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName

// Third-party libraries and standard library
import cats.effect.IO
import cats.implicits._
import org.slf4j.LoggerFactory
import io.circe.generic.auto._ // Assuming companion objects are now the standard

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

      // Step 2: 使用 API 验证艺术家是否存在
      _ <- verifyArtistExists()

      // Step 3: [已重构] 检查艺术家引用关系
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

  private def verifyArtistExists()(using PlanContext): IO[Unit] = {
    logInfo(s"正在通过 SearchUtil 确认艺术家是否存在: ${artistID}")
    SearchUtil.fetchArtistFromDB(artistID).flatMap { // <-- 直接调用
      case Some(_) => logInfo("艺术家存在，继续执行。")
      case None    => IO.raiseError(new Exception("艺术家ID不存在"))
    }
  }

  /**
   * 【已重构】
   * 检查艺术家是否被其他实体引用。
   * bandMembershipCheck 现在通过调用 SearchAllBelongingBands API 实现。
   */
  private def checkArtistIsNotReferenced()(using PlanContext): IO[Unit] = {
    logInfo(s"正在并行检查艺术家 ${artistID} 是否被其他实体引用...")

    // 检查是否被歌曲引用
    val songReferenceCheck: IO[Boolean] =
      FilterSongsByEntity(adminID, adminToken, Some(CreatorID_Type(CreatorType.Artist, artistID))).send.map {
        case (Some(songList), _) => songList.nonEmpty
        case _                   => false
      }

    // 【关键改动】直接调用 SearchUtil 检查是否是任何一个乐队的成员
    val bandMembershipCheck: IO[Boolean] = {
      logInfo(s"正在通过 SearchUtil 检查艺术家 ${artistID} 的乐队成员关系...")
      SearchUtil.findBandsByMemberFromDB(artistID).map(_.nonEmpty)
    }

    // 并行执行所有检查
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