// package Impl

// // 外部服务API的导入
// import APIs.CreatorService.GetBandByID // The API to fetch band details
// import APIs.OrganizeService.validateAdminMapping

// // 内部项目通用库的导入
// import Common.API.{PlanContext, Planner}
// import Common.DBAPI._
// import Common.Object.SqlParameter
// import Common.ServiceUtils.schemaName
// import Objects.CreatorService.Band // The authoritative Band object definition

// // 第三方库的导入
// import cats.effect.IO
// import cats.implicits._
// import io.circe.generic.auto._
// import io.circe.syntax._
// import org.slf4j.LoggerFactory

// /**
//  * Planner for AddBandManager: Handles adding a user as a manager for a band.
//  *
//  * This implementation has been refactored to call the GetBandByID API
//  * for fetching band data, rather than accessing the database directly.
//  *
//  * @param adminID     The administrator's user ID performing the action.
//  * @param adminToken  The administrator's authentication token.
//  * @param userID      The user ID to be added as a manager.
//  * @param bandID      The ID of the band to be managed.
//  * @param planContext The implicit execution context.
//  */
// case class AddBandManagerPlanner(
//   adminID: String,
//   adminToken: String,
//   userID: String,
//   bandID: String,
//   override val planContext: PlanContext
// ) extends Planner[(Boolean, String)] { // Aligned with the official API return type

//   private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

//   override def plan(using planContext: PlanContext): IO[(Boolean, String)] = {
//     val logic: IO[(Boolean, String)] = for {
//       // Step 1: Validate administrator credentials.
//       _ <- validateAdmin()

//       // Step 2: Verify that the target user exists.
//       _ <- verifyUserExists()

//       // Step 3: Fetch the band's information via its dedicated API.
//       band <- getBandViaAPI()

//       // Step 4: Ensure the user is not already a manager for the band.
//       _ <- checkNotAlreadyManager(band)

//       // Step 5: Add the user to the band's manager list and persist the change.
//       _ <- updateBandManagerList(band)
//     } yield (true, "管理者添加成功")

//     // Unified error handling for the entire process.
//     logic.handleErrorWith { error =>
//       logError(s"为乐队 ${bandID} 添加管理者 ${userID} 的操作失败", error) >>
//         IO.pure((false, error.getMessage))
//     }
//   }

//   /** Validates the admin credentials by calling the authentication service. */
//   private def validateAdmin()(using PlanContext): IO[Unit] = {
//     logInfo(s"正在验证管理员凭证: adminID=${adminID}") >>
//       validateAdminMapping(adminID, adminToken).send.flatMap {
//         case (true, _) => logInfo("管理员验证通过。")
//         case (false, errorMsg) => IO.raiseError(new Exception(s"管理员认证失败: $errorMsg"))
//       }
//   }

//   /** Verifies that the user to be added exists in the database. */
//   private def verifyUserExists()(using PlanContext): IO[Unit] = {
//     logInfo(s"正在检查用户是否存在: userID=${userID}") >> {
//       val query = s"SELECT EXISTS(SELECT 1 FROM ${schemaName}.user WHERE user_id = ?)"
//       readDBBoolean(query, List(SqlParameter("String", userID))).flatMap {
//         case true  => logInfo(s"用户(userID=${userID})存在。")
//         case false => IO.raiseError(new Exception("指定的用户不存在"))
//       }
//     }
//   }

//   /** Fetches band information by calling the GetBandByID API. */
//   private def getBandViaAPI()(using PlanContext): IO[Band] = {
//     logInfo(s"正在通过 GetBandByID API 查询乐队: bandID=${bandID}")
//     // The admin uses their own credentials to authorize the lookup.
//     GetBandByID(userID = adminID, userToken = adminToken, bandID = bandID).send.flatMap {
//       case (Some(band), _) =>
//         logInfo(s"API查询成功，找到乐队: ${band.name}")
//         IO.pure(band)
//       case (None, message) =>
//         IO.raiseError(new Exception(message))
//     }
//   }

//   /** Checks if the user is already in the band's managedBy list. */
//   private def checkNotAlreadyManager(band: Band)(using PlanContext): IO[Unit] = {
//     logInfo(s"正在检查用户 ${userID} 是否已是乐队 ${bandID} 的管理者") >> {
//       if (band.managedBy.contains(userID)) {
//         IO.raiseError(new Exception("该用户已经是此乐队的管理者"))
//       } else {
//         logInfo(s"用户 ${userID} 不是管理者，可以继续。")
//       }
//     }
//   }

//   /** Adds the new user ID to the band's managedBy list and updates the database. */
//   private def updateBandManagerList(band: Band)(using PlanContext): IO[Unit] = {
//     val updatedList = (band.managedBy :+ userID).distinct // Use distinct just in case
//     val query = s"""UPDATE "${schemaName}"."band_table" SET managed_by = ? WHERE band_id = ?"""
//     logInfo(s"正在更新乐队 ${bandID} 的管理者列表") >>
//       writeDB(
//         query,
//         List(
//           SqlParameter("String", updatedList.asJson.noSpaces),
//           SqlParameter("String", band.bandID)
//         )
//       ).void
//   }

//   /** Logs an informational message with the trace ID. */
//   private def logInfo(message: String): IO[Unit] =
//     IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))

//   /** Logs an error message with the trace ID and the cause. */
//   private def logError(message: String, cause: Throwable): IO[Unit] =
//     IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
// }