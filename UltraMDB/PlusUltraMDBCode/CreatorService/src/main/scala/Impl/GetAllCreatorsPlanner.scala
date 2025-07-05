package Impl

// External service APIs
import APIs.OrganizeService.validateUserMapping
import Objects.CreatorService.{CreatorID_Type, CreatorType} // 导入我们需要的类型

// Internal project common libraries
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName

// Third-party libraries and standard library
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory

/**
 * Planner for GetAllCreators: Fetches a complete list of all creators (artists and bands).
 *
 * @param userID     The ID of the user making the request.
 * @param userToken  The user's authentication token.
 * @param planContext The implicit execution context.
 */
case class GetAllCreatorsPlanner(
  userID: String,
  userToken: String,
  override val planContext: PlanContext
) extends Planner[(Option[List[CreatorID_Type]], String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Option[List[CreatorID_Type]], String)] = {
    val logic: IO[(Option[List[CreatorID_Type]], String)] = for {
      // 步骤 1: 验证用户身份
      _ <- validateUser()

      // 步骤 2: 并行获取所有艺术家和乐队
      _ <- logInfo("开始并行获取所有艺术家和乐队...")
      creatorsTuple <- (fetchAllArtists(), fetchAllBands()).parTupled
      (artists, bands) = creatorsTuple
      _ <- logInfo(s"获取到 ${artists.length} 位艺术家和 ${bands.length} 支乐队。")
      
      // 合并结果
      allCreators = artists ++ bands
      
    } yield {
      // 步骤 3: 格式化成功响应
      val message = if (allCreators.isEmpty) "数据库中没有任何创作者" else "查询成功"
      (Some(allCreators), message)
    }

    // 统一的错误处理
    logic.handleErrorWith { error =>
      logError("获取所有创作者的操作失败", error) >>
        IO.pure((None, error.getMessage))
    }
  }

  /**
   * 验证用户凭证
   */
  private def validateUser()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证用户身份: userID=${userID}")
    validateUserMapping(userID, userToken).send.flatMap {
      case (true, _) => logInfo("用户验证通过。")
      case (false, message) => IO.raiseError(new Exception(s"用户认证失败: $message"))
    }
  }

  /**
   * 从数据库中获取所有艺术家的ID，并将其包装为 CreatorID_Type
   * @return 一个包含所有艺术家 CreatorID_Type 的列表
   */
  private def fetchAllArtists()(using PlanContext): IO[List[CreatorID_Type]] = {
    logInfo("正在查询 artist_table...")
    val query = s"""SELECT artist_id FROM "${schemaName}"."artist_table""""
    
    readDBRows(query, List()).flatMap { rows =>
      rows.traverse { row =>
        IO.fromEither(row.hcursor.get[String]("artistID").map(CreatorID_Type.artist))
      }.handleErrorWith { err =>
        IO.raiseError(new Exception(s"解码 artistID 失败: ${err.getMessage}"))
      }
    }
  }

  /**
   * 从数据库中获取所有乐队的ID，并将其包装为 CreatorID_Type
   * @return 一个包含所有乐队 CreatorID_Type 的列表
   */
  private def fetchAllBands()(using PlanContext): IO[List[CreatorID_Type]] = {
    logInfo("正在查询 band_table...")
    val query = s"""SELECT band_id FROM "${schemaName}"."band_table""""
    
    readDBRows(query, List()).flatMap { rows =>
      rows.traverse { row =>
        IO.fromEither(row.hcursor.get[String]("bandID").map(CreatorID_Type.band))
      }.handleErrorWith { err =>
        IO.raiseError(new Exception(s"解码 bandID 失败: ${err.getMessage}"))
      }
    }
  }

  private def logInfo(message: String): IO[Unit] = IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))
  private def logError(message: String, cause: Throwable): IO[Unit] = IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}