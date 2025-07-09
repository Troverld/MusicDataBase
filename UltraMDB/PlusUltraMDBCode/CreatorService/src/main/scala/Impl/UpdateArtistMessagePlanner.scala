package Impl

// 外部服务API的导入
// import APIs.CreatorService.{GetArtistByID, ValidArtistOwnership}

import APIs.OrganizeService.validateAdminMapping
import Utils.SearchUtil

// 内部项目通用库的导入
import Common.API.{PlanContext, Planner}
import Objects.CreatorService.Artist
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName

// 第三方库的导入
import cats.effect.IO
import cats.implicits._
import org.slf4j.LoggerFactory
import io.circe.generic.auto.deriveEncoder // Temporary import

/**
 * Planner for UpdateArtistMessage: Handles updating an artist's name and/or bio.
 *
 * This implementation first validates ownership via the ValidArtistOwnership API,
 * then fetches the current artist state via the GetArtistByID API to apply updates.
 *
 * @param userID      The ID of the user initiating the update.
 * @param userToken   The user's authentication token.
 * @param artistID    The ID of the artist to update.
 * @param name        The optional new name for the artist.
 * @param bio         The optional new bio for the artist.
 * @param planContext The implicit execution context.
 */
case class UpdateArtistMessagePlanner(
  userID: String,
  userToken: String,
  artistID: String,
  name: Option[String],
  bio: Option[String],
  override val planContext: PlanContext
) extends Planner[(Boolean, String)] { // Corrected return type

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Boolean, String)] = {
    // Check if there's anything to update at all.
    if (name.isEmpty && bio.isEmpty) {
      return IO.pure((false, "没有提供任何更新内容"))
    }

    val logic: IO[(Boolean, String)] = for {
      // Step 1: Verify the user has the right to perform this action.
      // This single call handles both user authentication and ownership verification.
      _ <- verifyOwnership()

      // Step 2: Fetch the current artist data to use as a base for the update.
      currentArtist <- getArtist()

      // Step 3: Apply the updates and persist to the database.
      _ <- updateArtist(currentArtist)
    } yield (true, "艺术家信息更新成功")

    logic.handleErrorWith { error =>
      logError(s"更新艺术家 ${artistID} 的操作失败", error) >>
        IO.pure((false, error.getMessage))
    }
  }

  /**
   * Verifies that the user has ownership rights over the artist.
   * This delegates the check to the dedicated validation API.
   */
  private def verifyOwnership()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证用户 ${userID} 对艺术家 ${artistID} 的管理权限")
    validateAdminMapping(userID, userToken).send.flatMap {
      case (true, _) =>
        logInfo("权限验证通过。")
      case (false, message) =>
        IO.raiseError(new Exception(s"权限验证失败: $message"))
    }
  }

  /**
   * Fetches the current state of the artist using the GetArtistByID API.
   * This is necessary to fill in any fields that are not being updated.
   */
  private def getArtist()(using PlanContext): IO[Artist] = {
    logInfo(s"正在通过 SearchUtil 获取艺术家 ${artistID} 的当前信息")
    SearchUtil.fetchArtistFromDB(artistID).flatMap { // <-- 直接调用
      case Some(artist) => IO.pure(artist)
      case None => IO.raiseError(new Exception(s"无法获取艺术家信息: ID ${artistID} 不存在"))
    }
  }

  /**
   * Updates the artist's information in the database with the new values.
   */
  private def updateArtist(currentArtist: Artist)(using PlanContext): IO[Unit] = {
    // Use the provided new value, or fall back to the current value if None.
    val newName = name.getOrElse(currentArtist.name)
    val newBio = bio.getOrElse(currentArtist.bio)

    logInfo(s"正在执行数据库更新。新名称: '$newName', 新简介: '$newBio'")

    val query =
      s"""
         UPDATE "${schemaName}"."artist_table"
         SET name = ?, bio = ?
         WHERE artist_id = ?
      """

    writeDB(
      query,
      List(
        SqlParameter("String", newName),
        SqlParameter("String", newBio),
        SqlParameter("String", artistID)
      )
    ).void
  }

  /** Logs an informational message with the trace ID. */
  private def logInfo(message: String): IO[Unit] =
    IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))

  /** Logs an error message with the trace ID and the cause. */
  private def logError(message: String, cause: Throwable): IO[Unit] =
    IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}