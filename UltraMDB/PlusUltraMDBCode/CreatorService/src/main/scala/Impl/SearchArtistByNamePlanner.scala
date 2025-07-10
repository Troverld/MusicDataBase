package Impl

// External service APIs
import APIs.OrganizeService.validateUserMapping

// Internal project common libraries
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName

// Third-party libraries and standard library
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._ // Assuming companion objects are the standard

import org.slf4j.LoggerFactory

/**
 * Planner for SearchArtistByName: Handles fuzzy searching for artists by name.
 *
 * @param userID     The ID of the user making the request.
 * @param userToken  The user's authentication token.
 * @param name The partial or full name of the artist to search for.
 * @param planContext The implicit execution context.
 */
case class SearchArtistByNamePlanner(
  userID: String,
  userToken: String,
  name: String,
  override val planContext: PlanContext
) extends Planner[(Option[List[String]], String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Option[List[String]], String)] = {
    val logic: IO[(Option[List[String]], String)] = for {
      // Step 1: Authenticate the user.
      _ <- validateUser()

      // Step 2: Validate the search input.
      _ <- validateInputs()

      // Step 3: Perform the fuzzy search in the database.
      artistIDs <- searchArtistsInDB()
    } yield {
      // Step 4: Format the successful response.
      val message = if (artistIDs.isEmpty) "未找到匹配的艺术家" else "查询成功"
      (Some(artistIDs), message)
    }

    // Unified error handling for the entire process.
    logic.handleErrorWith { error =>
      logError(s"搜索艺术家 '${name}' 的操作失败", error) >>
        IO.pure((None, error.getMessage))
    }
  }

  /**
   * Validates the user's credentials by calling the authentication service.
   */
  private def validateUser()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证用户身份: userID=${userID}")
    validateUserMapping(userID, userToken).send.flatMap {
      case (true, _) => logInfo("用户验证通过。")
      case (false, message) => IO.raiseError(new Exception(s"用户认证失败: $message"))
    }
  }

  /**
   * Validates that the search term is not blank.
   */
  private def validateInputs()(using PlanContext): IO[Unit] = {
    logInfo("正在验证输入参数...")
    if (name.trim.isEmpty) {
      IO.raiseError(new IllegalArgumentException("艺术家搜索名称不能为空"))
    } else {
      logInfo(s"搜索词验证通过: '${name}'")
    }
  }

  /**
   * Performs a fuzzy search in the artist_table using the LIKE operator.
   * @return A list of matching artist IDs.
   */
  private def searchArtistsInDB()(using PlanContext): IO[List[String]] = {
    logInfo(s"正在数据库中模糊搜索艺术家: name LIKE '%${name}%'")
    val query = s"""SELECT artist_id FROM "${schemaName}"."artist_table" WHERE name LIKE ?"""
    val searchParam = s"%$name%"

    readDBRows(query, List(SqlParameter("String", searchParam))).flatMap { rows =>
      // Safely traverse the list of JSON results and decode the 'artist_id' from each.
      rows.traverse { row =>
        IO.fromEither(row.hcursor.get[String]("artistID"))
      }.handleErrorWith { err =>
        IO.raiseError(new Exception(s"解码 artist_id 失败: ${err.getMessage}"))
      }
    }
  }

  private def logInfo(message: String): IO[Unit] = IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))
  private def logError(message: String, cause: Throwable): IO[Unit] = IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}