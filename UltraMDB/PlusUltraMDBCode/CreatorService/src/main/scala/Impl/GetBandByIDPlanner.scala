package Impl

// 外部服务API的导入
import APIs.OrganizeService.validateUserMapping // API for user authentication

// 内部项目通用库的导入
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import Objects.CreatorService.Band // The authoritative Band object definition

// 第三方库的导入
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory

/**
 * Planner for GetBandByID: Handles fetching a band's full metadata by its ID.
 *
 * @param userID      The ID of the user making the request.
 * @param userToken   The user's authentication token.
 * @param bandID      The unique ID of the band to query. (Note: Corrected from artistID in API spec to match intent).
 * @param planContext The implicit execution context.
 */
case class GetBandByIDPlanner(
  userID: String,
  userToken: String,
  bandID: String, // Corrected parameter name for clarity and correctness
  override val planContext: PlanContext
) extends Planner[(Option[Band], String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Option[Band], String)] = {
    val logic: IO[(Option[Band], String)] = for {
      // Step 1: Authenticate the user.
      _ <- validateUser()

      // Step 2: Fetch the band from the database.
      bandOpt <- fetchBand()
    } yield {
      // Step 3: Format the successful response.
      bandOpt match {
        case Some(band) => (Some(band), "查询成功")
        case None       => (None, "指定的乐队不存在")
      }
    }

    // Unified error handling for any failures in the logic chain.
    logic.handleErrorWith { error =>
      logError(s"查询乐队 ${bandID} 的操作失败", error) >>
        IO.pure((None, error.getMessage)) // Return (None, errorMessage) on failure.
    }
  }

  /** Validates the user's credentials by calling the authentication service. */
  private def validateUser()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证用户身份: userID=${userID}") >>
      validateUserMapping(userID, userToken).send.flatMap {
        case (true, _) =>
          logInfo("用户验证通过。")
        case (false, errorMsg) =>
          IO.raiseError(new Exception(s"用户认证失败: $errorMsg"))
      }
  }

  /**
   * Fetches a band from the database by its ID.
   * Returns an IO[Option[Band]] to gracefully handle the "not found" case.
   */
  private def fetchBand()(using PlanContext): IO[Option[Band]] = {
    logInfo(s"正在数据库中查询乐队: bandID=${bandID}")
    val query = s"""SELECT * FROM "${schemaName}"."band_table" WHERE band_id = ?"""
    readDBRows(query, List(SqlParameter("String", bandID))).flatMap {
      case row :: Nil =>
        // One band found, attempt to decode it.
        IO.fromEither(row.as[Band])
          .map(Some(_)) // On success, wrap in Some
          .handleErrorWith(err => IO.raiseError(new Exception(s"解码Band对象失败: ${err.getMessage}")))
      case Nil =>
        // No band found, return an empty Option.
        IO.pure(None)
      case _ =>
        // This case indicates a data integrity issue.
        IO.raiseError(new Exception(s"数据库主键冲突: 存在多个ID为 ${bandID} 的乐队"))
    }
  }

  /** Logs an informational message with the trace ID. */
  private def logInfo(message: String): IO[Unit] =
    IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))

  /** Logs an error message with the trace ID and the cause. */
  private def logError(message: String, cause: Throwable): IO[Unit] =
    IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}