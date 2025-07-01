package Impl

// External Service APIs
import APIs.OrganizeService.validateAdminMapping

// Internal Project Libraries
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import Objects.OrganizeService.{AuthRequest, RequestStatus}
import io.circe.generic.auto.deriveEncoder
import Objects.CreatorService.CreatorID_Type

// Third-party and Standard Libraries
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory

/**
 * Planner for GetAuthRequestListMessage: Fetches a paginated and optionally filtered
 * list of authorization requests for administrators.
 */
case class GetAuthRequestListMessagePlanner(
  adminID: String,
  adminToken: String,
  statusFilter: Option[RequestStatus], // Now receives the type-safe object
  pageNumber: Int,
  pageSize: Int,
  override val planContext: PlanContext
) extends Planner[(Option[List[AuthRequest]], String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  private val MAX_PAGE_SIZE = 100
  private val DEFAULT_PAGE_SIZE = 20

  override def plan(using planContext: PlanContext): IO[(Option[List[AuthRequest]], String)] = {
    val logic: IO[(Option[List[AuthRequest]], String)] = for {
      _ <- validateAdmin()
      // The validation logic is now much simpler.
      validatedInputs <- validateAndSanitizeInputs()
      requests <- getRequestsFromDB(validatedInputs)
    } yield (Some(requests), "查询成功")

    logic.handleErrorWith { error =>
      logError(s"获取认证申请列表失败", error) >>
        IO.pure((None, error.getMessage))
    }
  }

  // A small case class to hold validated parameters.
  private case class ValidatedInputs(
    status: Option[RequestStatus],
    limit: Int,
    offset: Int
  )

  private def validateAdmin()(using PlanContext): IO[Unit] = {
    logInfo("正在验证管理员身份...")
    validateAdminMapping(adminID, adminToken).send.flatMap {
      case (true, _) => logInfo("管理员验证通过。")
      case (false, message) => IO.raiseError(new Exception(s"管理员认证失败: $message"))
    }
  }

  /**
   * [SIMPLIFIED] Validates and sanitizes inputs. Status validation is now handled by Circe.
   */
  private def validateAndSanitizeInputs()(using PlanContext): IO[ValidatedInputs] = IO.defer {
    logInfo("正在验证和净化输入参数...")
    for {
      _ <- IO.raiseUnless(pageNumber > 0)(new IllegalArgumentException("页码必须大于0"))
      validatedPageSize = if (pageSize > 0 && pageSize <= MAX_PAGE_SIZE) pageSize else DEFAULT_PAGE_SIZE
      offset = (pageNumber - 1) * validatedPageSize
    } yield ValidatedInputs(statusFilter, validatedPageSize, offset) // Simply pass the statusFilter through
  }

  private def getRequestsFromDB(inputs: ValidatedInputs)(using PlanContext): IO[List[AuthRequest]] = {
    // This logic remains the same, but it's now safer because `inputs.status` is guaranteed to be valid.
    val statusClause = inputs.status.map(_ => "status = ?")
    val whereClauses = List(statusClause).flatten
    val whereSql = if (whereClauses.isEmpty) "" else s"WHERE ${whereClauses.mkString(" AND ")}"

    val query =
      s"""
         SELECT * FROM "${schemaName}"."auth_request_table"
         $whereSql
         ORDER BY created_at DESC
         LIMIT ? OFFSET ?
      """

    val params = inputs.status.map(s => SqlParameter("String", s.toString)).toList ++ List(
      SqlParameter("Int", inputs.limit.toString),
      SqlParameter("Int", inputs.offset.toString)
    )

    logInfo(s"执行数据库查询: $query with params: $params")
    readDBRows(query, params).flatMap { rows =>
      rows.traverse(row => IO.fromEither(row.as[AuthRequest]))
        .handleErrorWith(err => IO.raiseError(new Exception(s"解码AuthRequest列表失败: ${err.getMessage}")))
    }
  }

  private def logInfo(message: String): IO[Unit] = IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))
  private def logError(message: String, cause: Throwable): IO[Unit] = IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}