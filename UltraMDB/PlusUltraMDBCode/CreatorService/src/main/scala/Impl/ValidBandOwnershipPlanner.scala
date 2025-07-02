// package Impl

// // 外部服务API的导入
// import APIs.CreatorService.GetBandByID // The API to fetch band details
// import APIs.OrganizeService.validateAdminMapping // API to check for admin status

// // 内部项目通用库的导入
// import Common.API.{PlanContext, Planner}
// import Objects.CreatorService.Band

// // 第三方库的导入
// import cats.effect.IO
// import cats.implicits._
// import org.slf4j.LoggerFactory
// import io.circe.generic.auto.deriveEncoder // Temporary import to satisfy the compiler

// /**
//  * Planner for ValidBandOwnership: Checks if a user has management rights over a specific band.
//  *
//  * A user is considered to have ownership if EITHER of the following is true:
//  * 1. They are listed in the band's `managedBy` field.
//  * 2. They are a system administrator.
//  *
//  * @param userID      The ID of the user whose ownership is being checked.
//  * @param userToken   The user's authentication token.
//  * @param bandID      The ID of the band in question.
//  * @param planContext The implicit execution context.
//  */
// case class ValidBandOwnershipPlanner(
//   userID: String,
//   userToken: String,
//   bandID: String,
//   override val planContext: PlanContext
// ) extends Planner[(Boolean, String)] { // Corrected to return (Boolean, String)

//   private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

//   override def plan(using planContext: PlanContext): IO[(Boolean, String)] = {
//     val logic: IO[(Boolean, String)] = for {
//       // Step 1: Fetch band information via its dedicated API.
//       bandOpt <- getBandViaAPI()

//       // Step 2: Concurrently, check if the current user is an administrator.
//       isAdmin <- checkIsAdmin()

//     } yield {
//       // Step 3: Combine the results with pure, clear logic.
//       // Check if the user is in the band's management list.
//       val isInManagedByList = bandOpt.exists(_.managedBy.contains(userID))

//       // Final ownership is true if the user is in the list OR is an admin.
//       val hasOwnership = isInManagedByList || isAdmin

//       val message = if (hasOwnership) "用户拥有管理权限" else "用户无管理权限"
//       (hasOwnership, message)
//     }

//     // Unified error handling for unexpected system failures during API calls.
//     logic.handleErrorWith { error =>
//       logError(s"验证用户 ${userID} 对乐队 ${bandID} 的权限时发生错误", error) >>
//         IO.pure((false, error.getMessage))
//     }
//   }

//   /**
//    * Calls the GetBandByID API to fetch band details.
//    * This helper returns an IO[Option[Band]], abstracting away the raw API response.
//    */
//   private def getBandViaAPI()(using PlanContext): IO[Option[Band]] = {
//     logInfo(s"正在通过API获取乐队信息: bandID=${bandID}")
//     GetBandByID(userID, userToken, bandID).send.map { case (bandOpt, msg) =>
//       logInfo(s"GetBandByID API 响应: $msg")
//       bandOpt // Return only the Option[Band] part.
//     }
//   }

//   /**
//    * Checks if the current user is an administrator by calling the validation API.
//    */
//   private def checkIsAdmin()(using PlanContext): IO[Boolean] = {
//     logInfo(s"正在检查用户 ${userID} 是否为管理员")
//     validateAdminMapping(adminID = userID, adminToken = userToken).send.map { case (isAdmin, msg) =>
//       logInfo(s"validateAdminMapping API 响应: $msg")
//       isAdmin // Return only the Boolean part.
//     }
//   }

//   /** Logs an informational message with the trace ID. */
//   private def logInfo(message: String): IO[Unit] =
//     IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))

//   /** Logs an error message with the trace ID and the cause. */
//   private def logError(message: String, cause: Throwable): IO[Unit] =
//     IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
// }