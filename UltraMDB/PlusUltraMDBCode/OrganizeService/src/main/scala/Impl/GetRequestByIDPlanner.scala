// package Impl

// // External Service APIs
// import APIs.OrganizeService.validateAdminMapping

// // Internal Project Libraries
// import Common.API.{PlanContext, Planner}
// import Common.DBAPI._
// import Common.Object.SqlParameter
// import Common.ServiceUtils.schemaName
// import Objects.OrganizeService.AuthRequest // The return object
// import Objects.CreatorService.CreatorID_Type // Needed for AuthRequest's companion object implicits

// // Third-party and Standard Libraries
// import cats.effect.IO
// import cats.implicits._
// import io.circe.generic.auto._ // For AuthRequest decoding
// import org.slf4j.LoggerFactory

// /**
//  * Planner for GetRequestByID: Fetches a single, unified authorization request by its ID.
//  *
//  * @param adminID    The ID of the administrator making the request.
//  * @param adminToken The administrator's authentication token.
//  * @param requestID  The unique ID of the request to query.
//  * @param planContext The implicit execution context.
//  */
// case class GetRequestByIDPlanner(
//   adminID: String,
//   adminToken: String,
//   requestID: String,
//   override val planContext: PlanContext
// ) extends Planner[(Option[AuthRequest], String)] {

//   private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

//   override def plan(using planContext: PlanContext): IO[(Option[AuthRequest], String)] = {
//     val logic: IO[(Option[AuthRequest], String)] = for {
//       // Step 1: Authorize the administrator.
//       _ <- validateAdmin()

//       // Step 2: Fetch the request from the database.
//       requestOpt <- getRequestFromDB()
//     } yield {
//       // Step 3: Format the successful response.
//       requestOpt match {
//         case Some(request) => (Some(request), "查询成功")
//         case None          => (None, "未找到指定的申请记录")
//       }
//     }

//     // Unified error handling for the entire process.
//     logic.handleErrorWith { error =>
//       logError(s"查询申请记录 ${requestID} 的操作失败", error) >>
//         IO.pure((None, error.getMessage))
//     }
//   }

//   /**
//    * Validates the administrator's credentials.
//    */
//   private def validateAdmin()(using PlanContext): IO[Unit] = {
//     logInfo(s"正在验证管理员身份: adminID=${adminID}")
//     validateAdminMapping(adminID, adminToken).send.flatMap {
//       case (true, _) => logInfo("管理员验证通过。")
//       case (false, message) => IO.raiseError(new Exception(s"管理员认证失败: $message"))
//     }
//   }

//   /**
//    * Fetches the raw request from the database and decodes it into a type-safe AuthRequest object.
//    * @return An IO[Option[AuthRequest]] to gracefully handle the "not found" case.
//    */
//   private def getRequestFromDB()(using PlanContext): IO[Option[AuthRequest]] = {
//     logInfo(s"正在数据库中查询申请记录: requestID=${requestID}")
//     val query = s"""SELECT * FROM "${schemaName}"."auth_request_table" WHERE request_id = ?"""
    
//     readDBRows(query, List(SqlParameter("String", requestID))).flatMap {
//       case row :: Nil =>
//         // The magic happens here: Circe uses the given decoder in AuthRequest.object
//         // to automatically handle all fields, including the nested CreatorID_Type.
//         IO.fromEither(row.as[AuthRequest])
//           .map(Some(_)) // On success, wrap in Some
//           .handleErrorWith(err => IO.raiseError(new Exception(s"解码AuthRequest对象失败: ${err.getMessage}")))
//       case Nil =>
//         // No request found, return an empty Option.
//         IO.pure(None)
//       case _ =>
//         // Should not be reachable if request_id is a PRIMARY KEY.
//         IO.raiseError(new Exception(s"数据库主键冲突: 存在多个ID为 ${requestID} 的申请记录"))
//     }
//   }

//   private def logInfo(message: String): IO[Unit] = IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))
//   private def logError(message: String, cause: Throwable): IO[Unit] = IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
// }