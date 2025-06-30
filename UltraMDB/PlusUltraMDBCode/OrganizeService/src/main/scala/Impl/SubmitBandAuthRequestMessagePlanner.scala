package Impl

import APIs.OrganizeService.validateUserMapping
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import Objects.CreatorService.Band
import Objects.OrganizeService.RequestStatus
import cats.effect.IO
import cats.implicits._
import io.circe.Json
import io.circe.generic.auto.deriveEncoder
import org.slf4j.LoggerFactory

/**
 * Planner for SubmitBandAuthRequestMessage: 用户提交乐队绑定申请.
 *
 * @param userID        发起请求的用户ID
 * @param userToken     用户的认证令牌
 * @param bandID      目标乐队的ID
 * @param certification 用户提供的认证材料
 * @param planContext   隐式执行上下文
 */
case class SubmitBandAuthRequestMessagePlanner(
                                                  userID: String,
                                                  userToken: String,
                                                  bandID: String,
                                                  certification: String,
                                                  override val planContext: PlanContext
                                                ) extends Planner[(Option[String], String)] { // 1. 遵循API约定，修改返回类型

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Option[String], String)] = {
    // 2. 将核心逻辑包裹在try/catch块中，以实现健壮的错误处理
    val logic: IO[String] = for {
      // 步骤 1: 验证用户令牌
      _ <- validateUser()

      // 步骤 2: 验证乐队是否存在，并确认用户当前不是其管理员
      _ <- validateBandAndNotManager()

      // 步骤 3: 验证认证材料的合法性
      _ <- validateCertification()

      // 步骤 4: 检查是否存在待处理的相同申请
      _ <- checkForPendingRequests()

      // 步骤 5: 创建并插入新的申请记录
      newRequestID <- createNewRequest()

    } yield newRequestID

    logic.map { newId =>
      (Some(newId), "申请已成功提交，请等待管理员审核。")
    }.handleErrorWith { error =>
      logError(s"提交乐队认证申请失败", error) >>
        IO.pure((None, error.getMessage))
    }
  }

  /** 步骤1的实现：验证用户 */
  private def validateUser()(using PlanContext): IO[Unit] = {
    logInfo("步骤 1: 验证用户令牌") >>
      // 4. 精确处理API的(Boolean, String)返回类型
      validateUserMapping(userID, userToken).send.flatMap {
        case (true, _) =>
          logInfo(s"用户 ${userID} 验证通过")
        case (false, message) =>
          IO.raiseError(new Exception(s"用户令牌无效: $message"))
      }
  }

  /** 步骤2的实现：验证乐队及绑定关系 */
  private def validateBandAndNotManager()(using PlanContext): IO[Unit] = {
    logInfo(s"步骤 2: 检查乐队 ${bandID} 的存在性和绑定关系") >>
      getBandByID(bandID).flatMap {
        case Some(band) if band.managedBy.contains(userID) =>
          IO.raiseError(new Exception("用户已与该乐队绑定，无需重复申请"))
        case Some(_) =>
          logInfo(s"乐队 ${bandID} 存在且用户未绑定，验证通过")
        case None =>
          IO.raiseError(new Exception("乐队ID不存在"))
      }
  }

  /** 步骤3的实现：验证认证材料 */
  private def validateCertification()(using PlanContext): IO[Unit] = {
    logInfo("步骤 3: 验证认证材料") >> {
      val trimmedCert = certification.trim
      if (trimmedCert.isEmpty || trimmedCert.length > 255) {
        IO.raiseError(new Exception("认证证据格式不合法（不能为空，且长度不超过255个字符）"))
      } else {
        logInfo("认证材料验证通过")
      }
    }
  }

  /** 步骤4的实现：检查待处理的申请 */
  private def checkForPendingRequests()(using PlanContext): IO[Unit] = {
    logInfo(s"步骤 4: 检查用户 ${userID} 和乐队 ${bandID} 之间是否存在待处理的申请") >> {
      val sql =
        s"""
           SELECT COUNT(1) FROM ${schemaName}.band_auth_request_table
           WHERE user_id = ? AND band_id = ? AND status = ?
         """
      readDBInt(
        sql,
        List(
          SqlParameter("String", userID),
          SqlParameter("String", bandID),
          SqlParameter("String", RequestStatus.Pending.toString)
        )
      ).flatMap { count =>
        if (count > 0) {
          IO.raiseError(new Exception("已有待完成的绑定申请，请勿重复提交"))
        } else {
          logInfo("未发现待处理的申请")
        }
      }
    }
  }

  /** 步骤5的实现：创建新申请 */
  private def createNewRequest()(using PlanContext): IO[String] = {
    logInfo("步骤 5: 插入新的申请记录到数据库") >> {
      val newRequestID = java.util.UUID.randomUUID().toString
      val sql =
        s"""
           INSERT INTO ${schemaName}.band_auth_request_table
           (request_id, user_id, band_id, certification, status, created_at)
           VALUES (?, ?, ?, ?, ?, NOW())
         """
      writeDB(
        sql,
        List(
          SqlParameter("String", newRequestID),
          SqlParameter("String", userID),
          SqlParameter("String", bandID),
          SqlParameter("String", certification),
          SqlParameter("String", RequestStatus.Pending.toString)
        )
      ).as(newRequestID) // .as(value) 是 .map(_ => value) 的简写
    }
  }

  /** 辅助方法：通过ID获取乐队信息 */
  private def getBandByID(id: String)(using PlanContext): IO[Option[Band]] = {
    val sql = s"SELECT * FROM ${schemaName}.band WHERE band_id = ?"
    // 5. 使用更安全的数据库查询和解码方式
    readDBRows(sql, List(SqlParameter("String", id))).flatMap {
      case row :: Nil =>
        IO.fromEither(row.as[Band])
          .map(Option.apply)
          .handleErrorWith(err => IO.raiseError(new Exception(s"解码Band失败: ${err.getMessage}")))
      case Nil => IO.pure(None)
      case _   => IO.raiseError(new Exception(s"数据库中存在多个相同的乐队ID: $id"))
    }
  }

  // 6. 统一格式的日志方法
  private def logInfo(message: String): IO[Unit] = IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))
  private def logError(message: String, cause: Throwable): IO[Unit] = IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}