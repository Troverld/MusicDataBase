package Impl

// 外部服务API的导入
import APIs.CreatorService.GetArtistByID // 用于验证 artistID 是否存在
import APIs.OrganizeService.validateUserMapping

// 内部项目通用库的导入
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import io.circe.generic.auto._ // 确保自动派生导入

// 第三方库及标准库的导入
import cats.effect.IO
import cats.implicits._
import io.circe.Json
import org.slf4j.LoggerFactory

/**
 * Planner for SearchAllBelongingBands: Finds all bands a specific artist is a member of.
 *
 * @param userID     The ID of the user making the request.
 * @param userToken  The user's authentication token.
 * @param artistID   The ID of the artist to search for in band memberships.
 * @param planContext The implicit execution context.
 */
case class SearchAllBelongingBandsPlanner(
  userID: String,
  userToken: String,
  artistID: String,
  override val planContext: PlanContext
) extends Planner[(Option[List[String]], String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Option[List[String]], String)] = {
    val logic: IO[(Option[List[String]], String)] = for {
      // Step 1: 验证用户身份
      _ <- validateUser()

      // Step 2: 验证目标艺术家是否存在，这是一个好的实践，可以提前返回清晰的错误信息
      _ <- verifyArtistExists()

      // Step 3: 在数据库中执行查询
      bandIDs <- findBandsByMember()

    } yield {
      // Step 4: 格式化成功响应
      val message = if (bandIDs.isEmpty) "该艺术家不属于任何乐队" else "查询成功"
      (Some(bandIDs), message)
    }

    // 统一错误处理
    logic.handleErrorWith { error =>
      logError(s"查询艺术家 ${artistID} 所属乐队的操作失败", error) >>
        IO.pure((None, error.getMessage))
    }
  }

  /**
   * 验证用户身份
   */
  private def validateUser()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证用户身份: userID=${userID}")
    validateUserMapping(userID, userToken).send.flatMap {
      case (true, _) => logInfo("用户验证通过。")
      case (false, message) => IO.raiseError(new Exception(s"用户认证失败: $message"))
    }
  }

  /**
   * 使用 GetArtistByID API 验证目标艺术家是否存在。
   * 这可以防止对无效 artistID 进行昂贵的数据库查询。
   */
  private def verifyArtistExists()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证艺术家是否存在: ${artistID}")
    GetArtistByID(userID, userToken, artistID).send.flatMap {
      case (Some(_), _) => logInfo("艺术家存在，继续查询。")
      case (None, _)    => IO.raiseError(new Exception("指定的艺术家ID不存在"))
    }
  }

  /**
   * 在 band_table 中查询 members 字段包含指定 artistID 的所有记录。
   *
   * @return A list of matching band IDs.
   */
  private def findBandsByMember()(using PlanContext): IO[List[String]] = {
    logInfo(s"正在数据库中搜索包含成员 ${artistID} 的乐队...")

    val query = s"""SELECT band_id FROM "${schemaName}"."band_table" WHERE members::jsonb @> ?::jsonb"""
    val artistIdAsJsonArray = s"""["$artistID"]"""
    val params = List(SqlParameter("String", artistIdAsJsonArray))

    readDBRows(query, params).flatMap { rows =>
      // 参照 SearchBandByNamePlanner 的正确实现进行修正。
      // 我们的数据库服务层会将 snake_case (band_id) 转换为 camelCase (bandID)。
      // 因此，我们必须使用 "bandID" 来解码 JSON。
      rows.traverse { row =>
        // 使用 IO.fromEither 可以使代码更简洁，它直接将 Either[Throwable, A] 转换为 IO[A]。
        IO.fromEither(row.hcursor.get[String]("bandID"))
      }.handleErrorWith { error =>
        // 提供一个更清晰的错误上下文。
        IO.raiseError(new Exception(s"从数据库行解码 bandID 失败: ${error.getMessage}", error))
      }
    }
  }

  private def logInfo(message: String): IO[Unit] = IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))
  private def logError(message: String, cause: Throwable): IO[Unit] = IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}