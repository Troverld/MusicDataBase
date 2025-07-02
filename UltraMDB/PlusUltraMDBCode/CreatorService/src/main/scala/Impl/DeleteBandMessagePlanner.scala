package Impl

// External service APIs
import APIs.CreatorService.GetBandByID // <--- 关键导入
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
import io.circe.generic.auto._ // Assuming companion objects are now the standard

case class DeleteBandMessagePlanner(
  adminID: String,
  adminToken: String,
  bandID: String,
  override val planContext: PlanContext
) extends Planner[(Boolean, String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Boolean, String)] = {
    val logic: IO[(Boolean, String)] = for {
      // Step 1: 验证管理员权限
      _ <- verifyIsAdmin()

      // Step 2: [已重构] 使用 API 验证乐队是否存在
      _ <- verifyBandExists()

      // Step 3: 检查乐队引用关系
      _ <- checkBandIsNotReferenced()

      // Step 4: [保持不变] 执行最终的数据库删除操作
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

  /**
   * 【已重构】
   * 使用 GetBandByID API 检查乐队是否存在。如果不存在则失败。
   */
  private def verifyBandExists()(using PlanContext): IO[Unit] = {
    logInfo(s"正在通过 API 确认乐队是否存在: ${bandID}")
    // 调用 GetBandByID API
    GetBandByID(adminID, adminToken, bandID).send.flatMap {
      // API 返回一个元组 (Option[Band], String)
      case (Some(_), _) =>
        // 如果 Option[Band] 是 Some，说明乐队存在
        logInfo("乐队存在，继续执行。")
      case (None, _) =>
        // 如果是 None，说明乐队不存在，抛出错误
        IO.raiseError(new Exception("乐队ID不存在"))
    }
  }

  private def checkBandIsNotReferenced()(using PlanContext): IO[Unit] = {
    logInfo(s"正在并行检查乐队 ${bandID} 是否被其他实体引用...")

    val songReferenceCheck: IO[Boolean] =
      FilterSongsByEntity(adminID, adminToken, entityID = Some(bandID), entityType = Some("band")).send.map {
        case (Some(songList), _) => songList.nonEmpty
        case _                   => false
      }
    
    songReferenceCheck.flatMap { isReferenced =>
      if (isReferenced) {
        IO.raiseError(new Exception("无法删除：乐队已被歌曲引用"))
      } else {
        logInfo("引用检查通过，乐队未被引用。")
      }
    }
  }

  /**
   * [保持不变]
   * 直接从数据库中删除乐队记录。这是此 Planner 的核心写操作职责。
   */
  private def deleteBandFromDB()(using PlanContext): IO[Unit] = {
    logInfo(s"正在从数据库中删除乐队: ${bandID}")
    val sql = s"""DELETE FROM "${schemaName}"."band_table" WHERE band_id = ?"""
    writeDB(sql, List(SqlParameter("String", bandID))).void
  }

  private def logInfo(message: String): IO[Unit] = IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))
  private def logError(message: String, cause: Throwable): IO[Unit] = IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}