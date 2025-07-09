package Impl

// 外部服务API的导入
import Utils.SearchUtil
import APIs.OrganizeService.validateAdminMapping

// 内部项目通用库的导入
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import Objects.CreatorService.Band

// 第三方库的导入
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import io.circe.syntax._
import org.slf4j.LoggerFactory

/**
 * Planner for UpdateBandMessage: Handles updating a band's name, bio, and/or members.
 *
 * @param userID      The ID of the user initiating the update.
 * @param userToken   The user's authentication token.
 * @param bandID      The ID of the band to update.
 * @param name        The optional new name for the band.
 * @param members     The optional new list of member IDs.
 * @param bio         The optional new bio for the band.
 * @param planContext The implicit execution context.
 */
case class UpdateBandMessagePlanner(
  userID: String,
  userToken: String,
  bandID: String,
  name: Option[String],
  members: Option[List[String]],
  bio: Option[String],
  override val planContext: PlanContext
) extends Planner[(Boolean, String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Boolean, String)] = {
    // 如果没有任何更新内容，提前返回
    if (name.isEmpty && members.isEmpty && bio.isEmpty) {
      return IO.pure((false, "没有提供任何更新内容"))
    }

    val logic: IO[(Boolean, String)] = for {
      // Step 1: 验证用户权限
      _ <- verifyOwnership()
      // Step 2: 验证新的成员列表（如果提供）
      _ <- validateMemberIDs(members)
      // Step 3: 获取乐队当前信息
      currentBand <- getBand()
      // Step 4: 更新数据库
      _ <- updateBand(currentBand, members)
    } yield (true, "乐队信息更新成功")

    logic.handleErrorWith { error =>
      logError(s"更新乐队 ${bandID} 的操作失败", error) >>
        IO.pure((false, s"更新失败: ${error.getMessage}"))
    }
  }

  private def verifyOwnership()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证用户 ${userID} 对乐队 ${bandID} 的管理权限")
    // 假设管理员有权更新所有乐队信息
    validateAdminMapping(userID, userToken).send.flatMap {
      case (true, _) => logInfo("权限验证通过。")
      case (false, message) => IO.raiseError(new Exception(s"权限验证失败: $message"))
    }
  }

  /**
   * 验证提供的成员ID列表：
   * 1. 检查成员ID是否有重复。
   * 2. 并行验证每个ID是否都对应一个存在的艺术家。
   */
  private def validateMemberIDs(memberIDsOpt: Option[List[String]])(using PlanContext): IO[Unit] = {
    memberIDsOpt match {
      case Some(ids) if ids.nonEmpty =>
        val distinctIDs = ids.distinct
        if (distinctIDs.size < ids.size) {
          return IO.raiseError(new IllegalArgumentException("提供的成员列表中包含重复的艺术家ID"))
        }

        logInfo(s"正在批量验证 ${distinctIDs.size} 个唯一成员ID的有效性...")
        SearchUtil.fetchArtistsFromDB(distinctIDs).flatMap { foundArtists =>
          val foundIDs = foundArtists.map(_.artistID).toSet
          val invalidIDs = distinctIDs.toSet -- foundIDs
          if (invalidIDs.nonEmpty) {
            IO.raiseError(new Exception(s"部分成员ID无效或不存在: ${invalidIDs.mkString(", ")}"))
          } else {
            logInfo("所有提供的成员ID均有效。")
          }
        }
      case _ => IO.unit
    }
  }

  private def getBand()(using PlanContext): IO[Band] = {
    logInfo(s"正在通过 SearchUtil 获取乐队 ${bandID} 的当前信息")
    SearchUtil.fetchBandFromDB(bandID).flatMap { // <-- 直接调用
      case Some(band) => IO.pure(band)
      case None => IO.raiseError(new Exception(s"无法获取乐队信息: ID ${bandID} 不存在"))
    }
  }

  private def updateBand(currentBand: Band, newMembersOpt: Option[List[String]])(using PlanContext): IO[Unit] = {
    val finalName = name.getOrElse(currentBand.name)
    val finalBio = bio.getOrElse(currentBand.bio)
    // 如果提供了新成员列表，则使用它；否则，使用乐队当前的成员列表
    val finalMembers = newMembersOpt.getOrElse(currentBand.members).distinct

    logInfo(s"正在执行数据库更新。")
    val query =
      s"""
         UPDATE "${schemaName}"."band_table"
         SET name = ?, bio = ?, members = ?
         WHERE band_id = ?
      """

    writeDB(
      query,
      List(
        SqlParameter("String", finalName),
        SqlParameter("String", finalBio),
        SqlParameter("String", finalMembers.asJson.noSpaces), // 序列化去重后的成员列表
        SqlParameter("String", bandID)
      )
    ).void
  }

  private def logInfo(message: String): IO[Unit] = IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))
  private def logError(message: String, cause: Throwable): IO[Unit] = IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}