package Impl

// 外部服务API的导入
import APIs.CreatorService.{GetArtistByID, GetBandByID, ValidBandOwnership}

// 内部项目通用库的导入
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import Objects.CreatorService.{Artist, Band}

// 第三方库的导入
import cats.effect.IO
import cats.implicits._ // This import provides the .parTraverse extension method
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
    if (name.isEmpty && members.isEmpty && bio.isEmpty) {
      return IO.pure((false, "没有提供任何更新内容"))
    }

    val logic: IO[(Boolean, String)] = for {
      _ <- verifyOwnership()
      _ <- validateMemberIDs(members)
      currentBand <- getBand()
      _ <- updateBand(currentBand, members)
    } yield (true, "乐队信息更新成功")

    logic.handleErrorWith { error =>
      logError(s"更新乐队 ${bandID} 的操作失败", error) >>
        IO.pure((false, error.getMessage))
    }
  }

  private def verifyOwnership()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证用户 ${userID} 对乐队 ${bandID} 的管理权限")
    ValidBandOwnership(userID, userToken, bandID).send.flatMap {
      case (true, _) => logInfo("权限验证通过。")
      case (false, message) => IO.raiseError(new Exception(s"权限验证失败: $message"))
    }
  }

  /**
   * 并行验证提供的成员ID列表中的每个ID是否都对应一个存在的艺术家。
   */
  private def validateMemberIDs(memberIDsOpt: Option[List[String]])(using PlanContext): IO[Unit] = {
    memberIDsOpt match {
      case Some(ids) if ids.nonEmpty =>
        logInfo(s"正在并行验证 ${ids.length} 个成员ID的有效性...")
        // -- THE FIX IS HERE --
        // Call .parTraverse directly on the collection `ids`
        ids.parTraverse { memberID =>
          GetArtistByID(userID, userToken, memberID).send.map(result => memberID -> result._1.isDefined)
        }.flatMap { results =>
          // The rest of the logic remains the same, but now it's more robust
          val invalidResults = results.filterNot(_._2)
          if (invalidResults.nonEmpty) {
            val invalidIDs = invalidResults.map(_._1)
            IO.raiseError(new Exception(s"部分成员ID无效或不存在: ${invalidIDs.mkString(", ")}"))
          } else {
            logInfo("所有成员ID均有效。")
          }
        }
      case _ => IO.unit // No members provided, no validation needed.
    }
  }

  private def getBand()(using PlanContext): IO[Band] = {
    logInfo(s"正在获取乐队 ${bandID} 的当前信息")
    GetBandByID(userID, userToken, bandID).send.flatMap {
      case (Some(band), _) => logInfo("成功获取到乐队当前信息。").as(band)
      case (None, message) => IO.raiseError(new Exception(s"无法获取乐队信息: $message"))
    }
  }

  private def updateBand(currentBand: Band, newMembers: Option[List[String]])(using PlanContext): IO[Unit] = {
    val finalName = name.getOrElse(currentBand.name)
    val finalBio = bio.getOrElse(currentBand.bio)
    val finalMembers = newMembers.getOrElse(currentBand.members)

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
        SqlParameter("String", finalMembers.asJson.noSpaces),
        SqlParameter("String", bandID)
      )
    ).void
  }

  private def logInfo(message: String): IO[Unit] = IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))
  private def logError(message: String, cause: Throwable): IO[Unit] = IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}