package Impl

import APIs.CreatorService.AddArtistManager
import APIs.OrganizeService.ValidateAdminMapping
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import Objects.OrganizeService.RequestStatus
import cats.effect.IO
import cats.implicits._
import io.circe.{Decoder, Json}
import org.slf4j.LoggerFactory
import io.circe.generic.auto.deriveEncoder

case class ApproveArtistAuthRequestMessagePlanner(
  adminID: String,
  adminToken: String,
  requestID: String,
  approve: Boolean,
  override val planContext: PlanContext
) extends Planner[(Boolean, String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  // Private DTO to represent a row from the auth request table safely.
  // This is an implementation detail, not a new core domain type.
  private case class ArtistAuthRequest(
    userId: String,
    artistId: String,
    status: String
  )

  // A type-safe JSON decoder for the private DTO.
  private implicit val artistAuthRequestDecoder: Decoder[ArtistAuthRequest] =
    Decoder.forProduct3("user_id", "artist_id", "status")(ArtistAuthRequest.apply)

  override def plan(using planContext: PlanContext): IO[(Boolean, String)] = {
    val logic: IO[String] = for {
      _ <- logInfo(s"开始处理管理员 ${adminID} 对认证请求 ${requestID} 的审批操作，操作为: ${if(approve) "批准" else "拒绝"}")

      // 步骤 1: 验证管理员凭据和权限
      _ <- ValidateAdmin()

      // 步骤 2: 从数据库检索并验证请求记录
      requestRecord <- getArtistAuthRequest(requestID)
      _ <- verifyRequestStatus(requestRecord, RequestStatus.Pending)

      // 步骤 3: 根据approve参数处理批准或拒绝逻辑
      resultMessage <- if (approve) {
        approveRequest(requestRecord)
      } else {
        rejectRequest()
      }
    } yield resultMessage

    logic.map { successMessage =>
      (true, successMessage)
    }.handleErrorWith { error =>
      logError(s"处理请求 ${requestID} 时发生错误", error) >>
        IO.pure((false, error.getMessage))
    }
  }

  /** 步骤1的实现：验证管理员身份 */
  private def ValidateAdmin()(using PlanContext): IO[Unit] = {
    ValidateAdminMapping(adminID, adminToken).send.flatMap {
      case (true, _) =>
        logInfo(s"管理员 ${adminID} 验证通过")
      case (false, message) =>
        IO.raiseError[Unit](new Exception(s"管理员身份验证失败: $message"))
    }
  }

  /** 步骤2.1的实现：获取认证请求 */
  private def getArtistAuthRequest(requestID: String)(using PlanContext): IO[ArtistAuthRequest] = {
    val sql =
      s"""
         SELECT user_id, artist_id, status FROM ${schemaName}.artist_auth_request_table
         WHERE request_id = ?
       """
    logInfo(s"查询 ArtistAuthRequestTable 获取 requestID 为 ${requestID} 的申请记录") >>
      readDBRows(sql, List(SqlParameter("String", requestID))).flatMap {
        case row :: Nil =>
          IO.fromEither(row.as[ArtistAuthRequest])
            .handleErrorWith(decodingError => IO.raiseError(new Exception(s"解码认证请求失败: ${decodingError.getMessage}")))
        case Nil => IO.raiseError(new Exception(s"未找到认证请求: ${requestID}"))
        case _   => IO.raiseError(new Exception(s"找到多个认证请求: ${requestID}"))
      }
  }

  /** 步骤2.2的实现：验证请求状态 */
  private def verifyRequestStatus(request: ArtistAuthRequest, expectedStatus: RequestStatus)(using PlanContext): IO[Unit] = {
    if (request.status == expectedStatus.toString) {
      logInfo(s"申请记录 ${requestID} 的状态为 ${expectedStatus}，可以进行审核")
    } else {
      IO.raiseError(new Exception(s"申请记录 ${requestID} 的状态为 ${request.status}，无法进行审核"))
    }
  }

  /** 步骤3.1的实现：批准请求 */
  private def approveRequest(request: ArtistAuthRequest)(using PlanContext): IO[String] = {
    for {
      _ <- logInfo(s"将请求 ${requestID} 的状态更新为 Approved")
      _ <- updateRequestStatus(RequestStatus.Approved)

      _ <- logInfo(s"调用 AddArtistManager，为艺术家 ${request.artistId} 绑定管理员 ${request.userId}")
      _ <- AddArtistManager(adminID, adminToken, request.userId, request.artistId).send.flatMap {
        case (true, _) => IO.unit
        case (false, msg) => IO.raiseError[Unit](new Exception(s"添加艺术家管理员失败: $msg"))
      }
    } yield "请求已批准"
  }

  /** 步骤3.2的实现：拒绝请求 */
  private def rejectRequest()(using PlanContext): IO[String] = {
    logInfo(s"将请求 ${requestID} 的状态更新为 Rejected") >>
      updateRequestStatus(RequestStatus.Rejected).as("请求已拒绝")
  }

  /** 辅助方法：更新数据库中的请求状态 */
  private def updateRequestStatus(status: RequestStatus)(using PlanContext): IO[Unit] = {
    val sql =
      s"""
         UPDATE ${schemaName}.artist_auth_request_table
         SET status = ?
         WHERE request_id = ?
       """
    writeDB(
      sql,
      List(
        SqlParameter("String", status.toString),
        SqlParameter("String", requestID)
      )
    ).void // .void 将 IO[String] 转换为 IO[Unit]，丢弃其结果，这正是我们需要的
  }

  private def logInfo(message: String): IO[Unit] = IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))
  private def logError(message: String, cause: Throwable): IO[Unit] = IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}