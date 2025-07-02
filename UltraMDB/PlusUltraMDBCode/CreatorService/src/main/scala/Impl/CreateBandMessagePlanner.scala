// package Impl

// // 外部服务API的导入
// import APIs.CreatorService.GetArtistByID
// import APIs.OrganizeService.validateAdminMapping

// // 内部项目通用库的导入
// import Common.API.{PlanContext, Planner}
// import Common.DBAPI._
// import Common.Object.SqlParameter
// import Common.ServiceUtils.schemaName

// // 第三方库及标准库的导入
// import cats.effect.IO
// import cats.implicits._
// import io.circe.generic.auto._ // Temporary import
// import io.circe.syntax._
// import org.slf4j.LoggerFactory
// import java.util.UUID

// /**
//  * Planner for CreateBandMessage: Handles the creation of a new band.
//  * This action is restricted to administrators and requires all members to be valid artists.
//  *
//  * @param adminID    The ID of the administrator creating the band.
//  * @param adminToken The administrator's authentication token.
//  * @param name       The name of the new band.
//  * @param members    A list of artist IDs that form the band.
//  * @param bio        The biography of the new band.
//  * @param planContext The implicit execution context.
//  */
// case class CreateBandMessagePlanner(
//   adminID: String,
//   adminToken: String,
//   name: String,
//   members: List[String],
//   bio: String,
//   override val planContext: PlanContext
// ) extends Planner[(Option[String], String)] { // Corrected return type

//   private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

//   override def plan(using planContext: PlanContext): IO[(Option[String], String)] = {
//     val logic: IO[(Option[String], String)] = for {
//       // Step 1: Verify administrator credentials.
//       _ <- verifyIsAdmin()

//       // Step 2: Validate business rules for inputs (name, etc.).
//       _ <- validateInputs()

//       // Step 3: Validate that all provided member IDs are existing artists.
//       _ <- validateMembers(members)

//       // Step 4: Generate a unique ID for the new band.
//       bandID <- generateBandID()

//       // Step 5: Insert the new band record into the database.
//       _ <- insertBand(bandID, members)
//     } yield (Some(bandID), "乐队创建成功")

//     logic.handleErrorWith { error =>
//       logError(s"创建乐队 '${name}' 的操作失败", error) >>
//         IO.pure((None, error.getMessage))
//     }
//   }

//   private def verifyIsAdmin()(using PlanContext): IO[Unit] = {
//     logInfo(s"正在验证管理员权限: adminID=${adminID}")
//     validateAdminMapping(adminID, adminToken).send.flatMap {
//       case (true, _) => logInfo("管理员权限验证通过。")
//       case (false, message) => IO.raiseError(new Exception(s"管理员认证失败: $message"))
//     }
//   }

//   private def validateInputs()(using PlanContext): IO[Unit] = {
//     logInfo("正在验证输入参数...")
//     if (name.trim.isEmpty) {
//       IO.raiseError(new IllegalArgumentException("乐队名称不能为空"))
//     } else {
//       logInfo("输入参数验证通过。")
//     }
//   }

//   /**
//    * Validates that every ID in the members list corresponds to a real artist
//    * by calling GetArtistByID in parallel.
//    */
//   private def validateMembers(memberIDs: List[String])(using PlanContext): IO[Unit] = {
//     if (memberIDs.isEmpty) {
//       logInfo("成员列表为空，跳过验证。")
//       return IO.unit
//     }

//     logInfo(s"正在并行验证 ${memberIDs.length} 个成员ID的有效性...")
//     memberIDs.parTraverse { memberID =>
//       // For each memberID, call the API and pair the ID with the validation result (isDefined).
//       GetArtistByID(adminID, adminToken, memberID).send.map(result => memberID -> result._1.isDefined)
//     }.flatMap { results =>
//       // Filter for failures.
//       val invalidResults = results.filterNot(_._2)
//       if (invalidResults.nonEmpty) {
//         val invalidIDs = invalidResults.map(_._1)
//         IO.raiseError(new Exception(s"部分成员ID无效或不存在: ${invalidIDs.mkString(", ")}"))
//       } else {
//         logInfo("所有成员ID均有效。")
//       }
//     }
//   }

//   private def generateBandID()(using PlanContext): IO[String] = {
//     val newID = s"band_${UUID.randomUUID().toString}"
//     logInfo(s"生成新乐队ID: $newID").as(newID)
//   }

//   /**
//    * Inserts the new band record, ensuring JSON fields are stored as strings.
//    * The creating admin is set as the initial manager.
//    */
//   private def insertBand(bandID: String, members: List[String])(using PlanContext): IO[Unit] = {
//     logInfo(s"正在向数据库插入新乐队记录: ID=${bandID}")
//     val query =
//       s"""
//          INSERT INTO "${schemaName}"."band_table" (band_id, name, members, bio, managed_by)
//          VALUES (?, ?, ?, ?, ?)
//       """
    
//     // Correctly serialize lists to JSON strings for TEXT columns.
//     val membersJson = members.asJson.noSpaces
//     val managedByJson = List(adminID).asJson.noSpaces

//     writeDB(
//       query,
//       List(
//         SqlParameter("String", bandID),
//         SqlParameter("String", name),
//         SqlParameter("String", membersJson), // Corrected: Use "String" for JSON text
//         SqlParameter("String", bio),
//         SqlParameter("String", managedByJson)  // Corrected: Use "String" for JSON text
//       )
//     ).void
//   }

//   private def logInfo(message: String): IO[Unit] = IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))
//   private def logError(message: String, cause: Throwable): IO[Unit] = IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
// }

package Impl

// 外部服务API的导入
import APIs.CreatorService.GetArtistByID // 注意：这里是伪代码，实际应为 GetArtistByIDMessage 或类似
import APIs.OrganizeService.validateAdminMapping

// 内部项目通用库的导入
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import io.circe.generic.auto._ // Temporary import

// 第三方库及标准库的导入
import cats.effect.IO
import cats.implicits._
import io.circe.syntax._
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Planner for CreateBandMessage: Handles the creation of a new band.
 * This action is restricted to administrators and requires all members to be valid artists.
 *
 * @param adminID    The ID of the administrator creating the band.
 * @param adminToken The administrator's authentication token.
 * @param name       The name of the new band.
 * @param members    A list of artist IDs that form the band.
 * @param bio        The biography of the new band.
 * @param planContext The implicit execution context.
 */
case class CreateBandMessagePlanner(
  adminID: String,
  adminToken: String,
  name: String,
  members: List[String],
  bio: String,
  override val planContext: PlanContext
) extends Planner[(Option[String], String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Option[String], String)] = {
    val logic: IO[(Option[String], String)] = for {
      // Step 1: 验证管理员权限
      _ <- verifyIsAdmin()

      // Step 2: 验证输入参数
      _ <- validateInputs()

      // Step 3: 验证所有成员ID都是有效的艺术家
      _ <- validateMembers(members)

      // Step 4: 生成唯一的乐队ID
      bandID <- generateBandID()

      // Step 5: 将新乐队插入数据库
      _ <- insertBand(bandID, members)
    } yield (Some(bandID), "乐队创建成功")

    logic.handleErrorWith { error =>
      logError(s"创建乐队 '${name}' 的操作失败", error) >>
        IO.pure((None, s"创建乐队失败: ${error.getMessage}"))
    }
  }

  private def verifyIsAdmin()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证管理员权限: adminID=${adminID}")
    validateAdminMapping(adminID, adminToken).send.flatMap {
      case (true, _) => logInfo("管理员权限验证通过。")
      case (false, message) => IO.raiseError(new Exception(s"管理员认证失败: $message"))
    }
  }

  private def validateInputs()(using PlanContext): IO[Unit] = {
    logInfo("正在验证输入参数...")
    if (name.trim.isEmpty) {
      IO.raiseError(new IllegalArgumentException("乐队名称不能为空"))
    } else {
      logInfo("输入参数验证通过。")
    }
  }

  /**
   * 通过并行调用 GetArtistByID 来验证成员列表中每个ID的有效性
   */
  private def validateMembers(memberIDs: List[String])(using PlanContext): IO[Unit] = {
    if (memberIDs.isEmpty) {
      logInfo("成员列表为空，跳过验证。")
      return IO.unit
    }

    logInfo(s"正在并行验证 ${memberIDs.length} 个成员ID的有效性...")
    memberIDs.parTraverse { memberID =>
      // 为了减少不必要的网络开销和认证，这里假设 GetArtistByID API 的调用是必要的
      // 这里的 GetArtistByID 应该是一个 case class extends API[...]
      GetArtistByID(adminID, adminToken, memberID).send.map(result => memberID -> result._1.isDefined)
    }.flatMap { results =>
      val invalidResults = results.filterNot(_._2)
      if (invalidResults.nonEmpty) {
        val invalidIDs = invalidResults.map(_._1).mkString(", ")
        IO.raiseError(new Exception(s"部分成员ID无效或不存在: ${invalidIDs}"))
      } else {
        logInfo("所有成员ID均有效。")
      }
    }
  }

  private def generateBandID()(using PlanContext): IO[String] = {
    val newID = s"band_${UUID.randomUUID().toString}"
    logInfo(s"生成新乐队ID: $newID").as(newID)
  }

  /**
   * 将新乐队记录插入数据库。
   * members 字段被序列化为 JSON 字符串并存入 TEXT 列。
   */
  private def insertBand(bandID: String, members: List[String])(using PlanContext): IO[Unit] = {
    logInfo(s"正在向数据库插入新乐队记录: ID=${bandID}")
    
    // SQL 语句现在只包含 band_id, name, members, 和 bio
    val query =
      s"""
         INSERT INTO "${schemaName}"."band_table" (band_id, name, members, bio)
         VALUES (?, ?, ?, ?)
      """

    // 将 members 列表序列化为 JSON 字符串
    val membersJson = members.asJson.noSpaces

    // 参数列表现在只包含4个参数
    val params = List(
      SqlParameter("String", bandID),
      SqlParameter("String", name),
      SqlParameter("String", membersJson), // members 作为 JSON 字符串存储
      SqlParameter("String", bio)
    )

    writeDB(query, params).void
  }

  private def logInfo(message: String): IO[Unit] = IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))
  private def logError(message: String, cause: Throwable): IO[Unit] = IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}