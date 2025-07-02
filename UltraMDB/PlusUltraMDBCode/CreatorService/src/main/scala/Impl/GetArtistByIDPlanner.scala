// package Impl

// // 外部服务API的导入
// import APIs.OrganizeService.validateUserMapping // API for user authentication

// // 内部项目通用库的导入
// import Common.API.{PlanContext, Planner}
// import Common.DBAPI._
// import Common.Object.SqlParameter
// import Common.ServiceUtils.schemaName
// import Objects.CreatorService.Artist // The authoritative Artist object definition

// // 第三方库的导入
// import cats.effect.IO
// import cats.implicits._
// import io.circe.generic.auto._
// import org.slf4j.LoggerFactory

// /**
//  * Planner for GetArtistByID: Handles fetching an artist's full metadata by their ID.
//  *
//  * @param userID      The ID of the user making the request.
//  * @param userToken   The user's authentication token.
//  * @param artistID    The unique ID of the artist to query.
//  * @param planContext The implicit execution context.
//  */
// case class GetArtistByIDPlanner(
//   userID: String,
//   userToken: String,
//   artistID: String,
//   override val planContext: PlanContext
// ) extends Planner[(Option[Artist], String)] {

//   private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

//   override def plan(using planContext: PlanContext): IO[(Option[Artist], String)] = {
//     val logic: IO[(Option[Artist], String)] = for {
//       // Step 1: Authenticate the user. This is a prerequisite for any data access.
//       _ <- validateUser()

//       // Step 2: Fetch the artist from the database. This helper returns an Option.
//       artistOpt <- fetchArtist()
//     } yield {
//       // Step 3: Construct the final response based on whether the artist was found.
//       artistOpt match {
//         case Some(artist) => (Some(artist), "查询成功")
//         case None         => (None, "指定的艺术家不存在")
//       }
//     }

//     // Unified error handling: Catches failures from authentication or database operations.
//     logic.handleErrorWith { error =>
//       logError(s"查询艺术家 ${artistID} 的操作失败", error) >>
//         IO.pure((None, error.getMessage)) // On any failure, return (None, errorMessage)
//     }
//   }

//   /** Validates the user's credentials by calling the authentication service. */
//   private def validateUser()(using PlanContext): IO[Unit] = {
//     logInfo(s"正在验证用户身份: userID=${userID}") >>
//       validateUserMapping(userID, userToken).send.flatMap {
//         case (true, _) =>
//           logInfo("用户验证通过。")
//         case (false, errorMsg) =>
//           IO.raiseError(new Exception(s"用户认证失败: $errorMsg"))
//       }
//   }

//   /**
//    * Fetches an artist from the database by its ID.
//    * Returns an IO[Option[Artist]] to gracefully handle the "not found" case.
//    */
//   private def fetchArtist()(using PlanContext): IO[Option[Artist]] = {
//     logInfo(s"正在数据库中查询艺术家: artistID=${artistID}")
//     val query = s"SELECT * FROM ${schemaName}.artist_table WHERE artist_id = ?"
//     readDBRows(query, List(SqlParameter("String", artistID))).flatMap {
//       case row :: Nil =>
//         // One artist found, attempt to decode it.
//         IO.fromEither(row.as[Artist])
//           .map(Some(_)) // On success, wrap in Some
//           .handleErrorWith(err => IO.raiseError(new Exception(s"解码Artist对象失败: ${err.getMessage}")))
//       case Nil =>
//         // No artist found, return an empty Option.
//         IO.pure(None)
//       case _ =>
//         // This case should not be reachable if artist_id is a PRIMARY KEY.
//         IO.raiseError(new Exception(s"数据库主键冲突: 存在多个ID为 ${artistID} 的艺术家"))
//     }
//   }

//   /** Logs an informational message with the trace ID. */
//   private def logInfo(message: String): IO[Unit] =
//     IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))

//   /** Logs an error message with the trace ID and the cause. */
//   private def logError(message: String, cause: Throwable): IO[Unit] =
//     IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
// }

package Impl

import APIs.OrganizeService.validateUserMapping
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import Objects.CreatorService.Artist // 导入更新后的 Artist 定义
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._ // 确保自动派生导入
import org.slf4j.LoggerFactory

case class GetArtistByIDPlanner(
  userID: String,
  userToken: String,
  artistID: String,
  override val planContext: PlanContext
) extends Planner[(Option[Artist], String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Option[Artist], String)] = {
    val logic: IO[(Option[Artist], String)] = for {
      // 1. 用户认证
      _ <- validateUser()

      // 2. 从数据库获取艺术家信息
      artistOpt <- fetchArtist()

    } yield {
      // 3. 格式化成功响应
      artistOpt match {
        case Some(artist) => (Some(artist), "查询艺术家成功")
        case None         => (None, s"未找到ID为 ${artistID} 的艺术家")
      }
    }

    // 统一错误处理
    logic.handleErrorWith { error =>
      logError(s"查询艺术家 ${artistID} 的操作失败", error) >>
        IO.pure((None, s"查询艺术家失败: ${error.getMessage}"))
    }
  }

  /**
   * 验证用户身份
   */
  private def validateUser()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证用户身份: userID=${userID}")
    validateUserMapping(userID, userToken).send.flatMap {
      case (true, _) => logInfo("用户验证通过。")
      case (false, message) => IO.raiseError(new Exception(s"用户认证失败: $message"))
    }
  }

  /**
   * 从数据库中获取艺术家信息
   */
  private def fetchArtist()(using PlanContext): IO[Option[Artist]] = {
    logInfo(s"正在数据库中查询艺术家: artist_id = ${artistID}")
    val query = s"""SELECT * FROM "${schemaName}"."artist_table" WHERE artist_id = ?"""
    val params = List(SqlParameter("String", artistID))

    readDBRows(query, params).flatMap { rows =>
      rows.headOption match {
        case Some(row) =>
          // 使用 Circe 的自动解码，现在应该能直接成功
          IO.fromEither(row.as[Artist])
            .map(Some(_)) // 将解码成功的 Artist 包装在 Some 中
            .handleErrorWith { err =>
              // 如果还是解码失败，抛出包含详细信息的异常
              IO.raiseError(new Exception(s"解码Artist对象失败: ${err.getMessage}"))
            }
        case None =>
          // 如果查询结果为空，返回 None
          IO.pure(None)
      }
    }
  }

  private def logInfo(message: String): IO[Unit] = IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))
  private def logError(message: String, cause: Throwable): IO[Unit] = IO(logger.error(s"TTID=${planContext.traceID.id} -- $message", cause))
}