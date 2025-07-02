// package Impl

// // 外部服务API的导入
// import APIs.CreatorService.GetArtistByID
// import APIs.OrganizeService.validateAdminMapping // API to check for admin status

// // 内部项目通用库的导入
// import Common.API.{PlanContext, Planner}
// import Objects.CreatorService.Artist

// // 第三方库的导入
// import cats.effect.IO
// import cats.implicits._
// import org.slf4j.LoggerFactory
// import io.circe.generic.auto.deriveEncoder

// /**
//  * Planner for ValidArtistOwnership: Checks if a user has management rights over a specific artist.
//  *
//  * A user is considered to have ownership if EITHER of the following is true:
//  * 1. They are listed in the artist's `managedBy` field.
//  * 2. They are a system administrator.
//  *
//  * @param userID      The ID of the user whose ownership is being checked.
//  * @param userToken   The user's authentication token.
//  * @param artistID    The ID of the artist in question.
//  * @param planContext The implicit execution context.
//  */
// case class ValidArtistOwnershipPlanner(
//   userID: String,
//   userToken: String,
//   artistID: String,
//   override val planContext: PlanContext
// ) extends Planner[(Boolean, String)] {

//   private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

//   override def plan(using planContext: PlanContext): IO[(Boolean, String)] = {
//     val logic: IO[(Boolean, String)] = for {
//       // Step 1: Fetch the artist information. This will not fail if the artist doesn't exist.
//       artistOpt <- getArtistViaAPI()

//       // Step 2: Concurrently, check if the current user is an administrator.
//       isAdmin <- checkIsAdmin()

//     } yield {
//       // Step 3: Combine the results with pure logic.
//       // Check if the user is in the artist's management list.
//       // .exists is a safe and concise way to handle the Option. It returns false if artistOpt is None.
//       val isInManagedByList = artistOpt.exists(_.managedBy.contains(userID))

//       // Final ownership is true if either condition is met.
//       val hasOwnership = isInManagedByList || isAdmin

//       val message = if (hasOwnership) "用户拥有管理权限" else "用户无管理权限"
//       (hasOwnership, message)
//     }

//     // Unified error handling for any unexpected system failures during API calls.
//     logic.handleErrorWith { error =>
//       logError(s"验证用户 ${userID} 对艺术家 ${artistID} 的权限时发生错误", error) >>
//         IO.pure((false, error.getMessage))
//     }
//   }

//   /**
//    * Calls the GetArtistByID API.
//    * This helper gracefully handles the API's (Option[Artist], String) response,
//    * returning only the IO[Option[Artist]] for use in the main logic.
//    */
//   private def getArtistViaAPI()(using PlanContext): IO[Option[Artist]] = {
//     logInfo(s"正在通过API获取艺术家信息: artistID=${artistID}")
//     GetArtistByID(userID, userToken, artistID).send.map { case (artistOpt, msg) =>
//       logInfo(s"GetArtistByID API 响应: $msg")
//       artistOpt // Return the Option part of the tuple.
//     }
//   }

//   /**
//    * Checks if the current user is an administrator by calling the validation API.
//    * Note: We pass the user's credentials to the admin validation endpoint.
//    */
//   private def checkIsAdmin()(using PlanContext): IO[Boolean] = {
//     logInfo(s"正在检查用户 ${userID} 是否为管理员")
//     validateAdminMapping(adminID = userID, adminToken = userToken).send.map { case (isAdmin, msg) =>
//       logInfo(s"validateAdminMapping API 响应: $msg")
//       isAdmin // Return the Boolean part of the tuple.
//     }
//   }

//   /** Logs an informational message with the trace ID. */
//   private def logInfo(message: String): IO[Unit] =
//     IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))

//   /** Logs an error message with the trace ID and the cause. */
//   private def logError(message: String, cause: Throwable): IO[Unit] =
//     IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
// }