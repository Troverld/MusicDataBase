package Impl

// External Service APIs
import APIs.CreatorService.{GetArtistByID, GetBandByID}
import APIs.OrganizeService.validateUserMapping

// Internal Project Libraries
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import Objects.CreatorService.{Artist, Band, CreatorID_Type, CreatorType}
import Objects.OrganizeService.RequestStatus

// Third-party and Standard Libraries
import io.circe.generic.auto.deriveEncoder
import cats.effect.IO
import cats.implicits._
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Planner for SubmitAuthRequestMessage: 用户提交一个统一的实体绑定申请。
 */
case class SubmitAuthRequestMessagePlanner(
  userID: String,
  userToken: String,
  target: CreatorID_Type, // Receiving the typed object directly.
  certification: String,
  override val planContext: PlanContext
) extends Planner[(Option[String], String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Option[String], String)] = {
    // The logic is now cleaner, as the initial validation of the target is no longer needed.
    val logic: IO[String] = for {
      // Step 1: Validate the user's identity.
      _ <- validateUser()

      // Step 2: Validate the target entity exists and user is not already a manager.
      _ <- validateTargetAndNotManager()

      // Step 3: Validate the format of the certification materials.
      _ <- validateCertification()

      // Step 4: Check for existing pending requests to prevent duplicates.
      _ <- checkForPendingRequests()

      // Step 5: Create and insert the new authorization request.
      newRequestID <- createNewRequest()
    } yield newRequestID

    logic.map { newId =>
      (Some(newId), "申请已成功提交，请等待管理员审核。")
    }.handleErrorWith { error =>
      logError(s"提交实体认证申请失败 for target ${target.id}", error) >>
        IO.pure((None, error.getMessage))
    }
  }

  private def validateUser()(using PlanContext): IO[Unit] = {
    logInfo("步骤 1: 验证用户令牌") >>
      validateUserMapping(userID, userToken).send.flatMap {
        case (true, _) => logInfo(s"用户 ${userID} 验证通过")
        case (false, message) => IO.raiseError(new Exception(s"用户令牌无效: $message"))
      }
  }

  private def validateTargetAndNotManager()(using PlanContext): IO[Unit] = {
    logInfo(s"步骤 2: 通过API检查实体 ${target.id} 的存在性和绑定关系")
    
    val checkResult: IO[Option[List[String]]] = target.creatorType match {
      case CreatorType.Artist => GetArtistByID(userID, userToken, target.id).send.map(_._1.map(_.managedBy))
      case CreatorType.Band   => GetBandByID(userID, userToken, target.id).send.map(_._1.map(_.managedBy))
    }

    checkResult.flatMap {
      case Some(managedBy) if managedBy.contains(userID) =>
        IO.raiseError(new Exception(s"您已是该${target.creatorType}的管理者，无需重复申请"))
      case Some(_) =>
        logInfo(s"${target.creatorType} ${target.id} 存在且用户未绑定，验证通过")
      case None =>
        IO.raiseError(new Exception(s"指定的${target.creatorType}不存在"))
    }
  }

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

  private def checkForPendingRequests()(using PlanContext): IO[Unit] = {
    logInfo(s"步骤 4: 检查用户 ${userID} 和实体 ${target.id} 之间是否存在待处理的申请") >> {
      val sql =
        s"""
           SELECT 1 FROM "${schemaName}"."auth_request_table"
           WHERE user_id = ? AND target_id = ? AND target_type = ? AND status = ? LIMIT 1
         """
      readDBRows(
        sql,
        List(
          SqlParameter("String", userID),
          SqlParameter("String", target.id),
          SqlParameter("String", target.creatorType.toString),
          SqlParameter("String", RequestStatus.Pending.toString)
        )
      ).flatMap {
        case Nil => logInfo("未发现待处理的申请")
        case _   => IO.raiseError(new Exception("已有待处理的绑定申请，请勿重复提交"))
      }
    }
  }

  private def createNewRequest()(using PlanContext): IO[String] = {
    logInfo("步骤 5: 插入新的申请记录到数据库") >> {
      val newRequestID = UUID.randomUUID().toString
      val sql =
        s"""
           INSERT INTO "${schemaName}"."auth_request_table"
           (request_id, user_id, target_id, target_type, certification, status, created_at)
           VALUES (?, ?, ?, ?, ?, ?, NOW())
         """
      writeDB(
        sql,
        List(
          SqlParameter("String", newRequestID),
          SqlParameter("String", userID),
          SqlParameter("String", target.id),
          SqlParameter("String", target.creatorType.toString),
          SqlParameter("String", certification),
          SqlParameter("String", RequestStatus.Pending.toString)
        )
      ).as(newRequestID)
    }
  }

  private def logInfo(message: String): IO[Unit] = IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))
  private def logError(message: String, cause: Throwable): IO[Unit] = IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}