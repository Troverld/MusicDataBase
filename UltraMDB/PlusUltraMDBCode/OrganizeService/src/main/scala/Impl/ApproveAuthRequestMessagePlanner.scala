package Impl

// External Service APIs
import APIs.CreatorService.{AddArtistManager, AddBandManager}
import APIs.OrganizeService.{GetRequestByID, validateAdminMapping} // Import our new API

// Internal Project Libraries
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import Objects.OrganizeService.{AuthRequest, RequestStatus}
import Objects.CreatorService.{CreatorID_Type, CreatorType}
import io.circe.generic.auto.*

// Third-party and Standard Libraries
import cats.effect.IO
import cats.implicits._
import org.slf4j.LoggerFactory

/**
 * Planner for ApproveAuthRequestMessage: 管理员审核一个统一的实体绑定申请。
 * This implementation is fully decoupled, using GetRequestByID to fetch request details.
 */
case class ApproveAuthRequestMessagePlanner(
  adminID: String,
  adminToken: String,
  requestID: String,
  approve: Boolean,
  override val planContext: PlanContext
) extends Planner[(Boolean, String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Boolean, String)] = {
    val logic: IO[String] = for {
      _ <- logInfo(s"开始处理管理员 ${adminID} 对请求 ${requestID} 的审批，操作为: ${if (approve) "批准" else "拒绝"}")

      // Step 1: Validate administrator credentials.
      _ <- validateAdmin()

      // Step 2: Retrieve the request details via the dedicated API.
      request <- getRequestViaAPI(requestID)

      // Step 3: Verify the request is in a state that can be processed.
      _ <- verifyRequestIsPending(request)

      // Step 4: Branch logic based on the approval decision.
      resultMessage <- if (approve) approveRequest(request) else rejectRequest()

    } yield resultMessage

    logic.map { successMessage =>
      (true, successMessage)
    }.handleErrorWith { error =>
      logError(s"处理请求 ${requestID} 时发生错误", error) >>
        IO.pure((false, error.getMessage))
    }
  }

  private def validateAdmin()(using PlanContext): IO[Unit] = {
    validateAdminMapping(adminID, adminToken).send.flatMap {
      case (true, _) => logInfo(s"管理员 ${adminID} 验证通过")
      case (false, message) => IO.raiseError(new Exception(s"管理员身份验证失败: $message"))
    }
  }

  /**
   * [NEW] Fetches the request by calling the GetRequestByID API.
   * This replaces direct database access and promotes service decoupling.
   */
  private def getRequestViaAPI(reqID: String)(using PlanContext): IO[AuthRequest] = {
    logInfo(s"通过API获取申请记录: requestID=${reqID}")
    GetRequestByID(adminID, adminToken, reqID).send.flatMap {
      case (Some(request), _) =>
        logInfo("成功获取到申请记录。")
        IO.pure(request)
      case (None, message) =>
        // If the request is not found, or any other issue occurs, fail fast.
        IO.raiseError(new Exception(s"无法获取申请记录: $message"))
    }
  }

  private def verifyRequestIsPending(request: AuthRequest)(using PlanContext): IO[Unit] = {
    if (request.status == RequestStatus.Pending) {
      logInfo(s"申请记录 ${requestID} 的状态为 Pending，可以进行审核")
    } else {
      IO.raiseError(new Exception(s"申请记录 ${requestID} 的状态为 ${request.status}，无法进行审核"))
    }
  }

  private def approveRequest(request: AuthRequest)(using PlanContext): IO[String] = {
    // This logic remains the same, as it was already well-designed.
    val addManagerCall: IO[(Boolean, String)] = request.targetID.creatorType match {
      case CreatorType.Artist =>
        logInfo(s"调用 AddArtistManager for artist ${request.targetID.id}")
        AddArtistManager(adminID, adminToken, request.userID, request.targetID.id).send
      case CreatorType.Band =>
        logInfo(s"调用 AddBandManager for band ${request.targetID.id}")
        AddBandManager(adminID, adminToken, request.userID, request.targetID.id).send
    }

    for {
      _ <- logInfo(s"将请求 ${requestID} 的状态更新为 Approved")
      _ <- updateRequestStatus(RequestStatus.Approved)
      _ <- logInfo(s"执行添加管理员API调用")
      addManagerSuccessful <- addManagerCall
      _ <- addManagerSuccessful match {
        case (true, _) => IO.unit
        case (false, msg) => IO.raiseError[Unit](new Exception(s"添加管理员失败: $msg"))
      }
    } yield "请求已批准"
  }

  private def rejectRequest()(using PlanContext): IO[String] = {
    logInfo(s"将请求 ${requestID} 的状态更新为 Rejected") >>
      updateRequestStatus(RequestStatus.Rejected).as("请求已拒绝")
  }

  private def updateRequestStatus(status: RequestStatus)(using PlanContext): IO[Unit] = {
    val sql =
      s"""
         UPDATE "${schemaName}"."auth_request_table"
         SET status = ?, processed_by = ?, processed_at = NOW()
         WHERE request_id = ?
       """
    writeDB(
      sql,
      List(
        SqlParameter("String", status.toString),
        SqlParameter("String", adminID),
        SqlParameter("String", requestID)
      )
    ).void
  }

  private def logInfo(message: String): IO[Unit] = IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))
  private def logError(message: String, cause: Throwable): IO[Unit] = IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}