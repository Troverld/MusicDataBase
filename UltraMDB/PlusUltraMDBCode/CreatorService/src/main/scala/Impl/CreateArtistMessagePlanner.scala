package Impl

// 外部服务API的导入
import APIs.OrganizeService.validateAdminMapping

// 内部项目通用库的导入
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName

// 第三方库及标准库的导入
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._ // Temporary import
import io.circe.syntax._
import org.slf4j.LoggerFactory
import java.util.UUID // For generating truly unique IDs

/**
 * Planner for CreateArtistMessage: Handles the creation of a new artist entry.
 * This action is restricted to administrators only.
 *
 * @param adminID    The ID of the administrator performing the creation.
 * @param adminToken The administrator's authentication token.
 * @param name       The name of the new artist.
 * @param bio        The biography of the new artist.
 * @param planContext The implicit execution context.
 */
case class CreateArtistMessagePlanner(
  adminID: String,
  adminToken: String,
  name: String,
  bio: String,
  override val planContext: PlanContext
) extends Planner[(Option[String], String)] { // Corrected return type

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Option[String], String)] = {
    val logic: IO[(Option[String], String)] = for {
      // Step 1: Verify the user is a valid administrator.
      _ <- verifyIsAdmin()

      // Step 2: Validate that essential inputs are not blank.
      _ <- validateInputs()

      // Step 3: Generate a new, unique ID for the artist.
      artistID <- generateArtistID()

      // Step 4: Insert the new artist record into the database.
      _ <- insertArtist(artistID)
    } yield (Some(artistID), "艺术家创建成功")

    logic.handleErrorWith { error =>
      logError(s"创建艺术家 '${name}' 的操作失败", error) >>
        IO.pure((None, error.getMessage))
    }
  }

  /**
   * Verifies that the provided credentials belong to an administrator.
   */
  private def verifyIsAdmin()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证管理员权限: adminID=${adminID}")
    validateAdminMapping(adminID, adminToken).send.flatMap {
      case (true, _) => logInfo("管理员权限验证通过。")
      case (false, message) => IO.raiseError(new Exception(s"管理员认证失败: $message"))
    }
  }

  /**
   * Validates business rules for the input data, e.g., name cannot be empty.
   */
  private def validateInputs()(using PlanContext): IO[Unit] = {
    logInfo("正在验证输入参数...")
    if (name.trim.isEmpty) {
      IO.raiseError(new IllegalArgumentException("艺术家名称不能为空"))
    } else {
      logInfo("输入参数验证通过。")
    }
  }

  /**
   * Generates a new, unique artist ID using UUID.
   */
  private def generateArtistID()(using PlanContext): IO[String] = {
    val newID = s"artist_${UUID.randomUUID().toString}"
    logInfo(s"生成新艺术家ID: $newID").as(newID)
  }

  /**
   * Inserts the new artist record into the database.
   * Initializes `managed_by` with an empty JSON array '[]' for consistency.
   */
  private def insertArtist(artistID: String)(using PlanContext): IO[Unit] = {
    logInfo(s"正在向数据库插入新艺术家记录: ID=${artistID}")
    val query =
      s"""
         INSERT INTO "${schemaName}"."artist_table" (artist_id, name, bio, managed_by)
         VALUES (?, ?, ?, ?)
      """

    // Critical Fix: Initialize managed_by with an empty JSON array, not NULL.
    val emptyManagerListJson = List.empty[String].asJson.noSpaces

    writeDB(
      query,
      List(
        SqlParameter("String", artistID),
        SqlParameter("String", name),
        SqlParameter("String", bio),
        SqlParameter("String", emptyManagerListJson)
      )
    ).void
  }

  private def logInfo(message: String): IO[Unit] = IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))
  private def logError(message: String, cause: Throwable): IO[Unit] = IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}