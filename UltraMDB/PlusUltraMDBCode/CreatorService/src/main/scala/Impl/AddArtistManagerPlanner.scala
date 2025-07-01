package Impl

// 外部服务API的导入
import APIs.CreatorService.GetArtistByID // <-- 新增: 导入我们将要调用的API
import APIs.OrganizeService.validateAdminMapping

// 内部项目通用库的导入
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import Objects.CreatorService.Artist

// 第三方库的导入
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import io.circe.syntax._
import org.slf4j.LoggerFactory

/**
 * Planner for AddArtistManager: 处理为艺术家添加管理者的业务逻辑。
 *
 * 此实现已重构，不再直接查询数据库以获取艺术家信息，
 * 而是通过调用 GetArtistByID API 来实现，以促进服务解耦。
 *
 * @param adminID     执行操作的管理员ID。
 * @param adminToken  管理员的认证令牌。
 * @param userID      将被添加为管理者的用户ID。
 * @param artistID    目标艺术家的ID。
 * @param planContext 隐式执行上下文。
 */
case class AddArtistManagerPlanner(
  adminID: String,
  adminToken: String,
  userID: String,
  artistID: String,
  override val planContext: PlanContext
) extends Planner[(Boolean, String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Boolean, String)] = {
    val logic: IO[(Boolean, String)] = for {
      // 步骤 1: 验证管理员凭证
      _ <- validateAdmin()

      // 步骤 2: 验证目标用户是否存在
      _ <- verifyUserExists()

      // 步骤 3: 通过API获取艺术家信息
      artist <- getArtistViaAPI() // <-- 关键改动: 调用新方法

      // 步骤 4: 确保用户尚未成为该艺术家的管理者
      _ <- checkNotAlreadyManager(artist)

      // 步骤 5: 更新艺术家的管理者列表
      _ <- updateArtistManagerList(artist)
    } yield (true, "管理者添加成功")

    // 统一的错误处理
    logic.handleErrorWith { error =>
      logError(s"为艺术家 ${artistID} 添加管理者 ${userID} 的操作失败", error) >>
        IO.pure((false, error.getMessage))
    }
  }

  /** 验证管理员身份。*/
  private def validateAdmin()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证管理员凭证: adminID=${adminID}") >>
      validateAdminMapping(adminID, adminToken).send.flatMap {
        case (true, _) => logInfo("管理员验证通过。")
        case (false, errorMsg) => IO.raiseError(new Exception(s"管理员认证失败: $errorMsg"))
      }
  }

  /** 验证目标用户是否存在于数据库中。*/
  private def verifyUserExists()(using PlanContext): IO[Unit] = {
    logInfo(s"正在检查用户是否存在: userID=${userID}") >> {
      val query = s"SELECT EXISTS(SELECT 1 FROM ${schemaName}.user WHERE user_id = ?)"
      readDBBoolean(query, List(SqlParameter("String", userID))).flatMap {
        case true  => logInfo(s"用户(userID=${userID})存在。")
        case false => IO.raiseError(new Exception("指定的用户不存在"))
      }
    }
  }

  // --- 这是被替换的部分 ---
  // private def fetchArtist()(using PlanContext): IO[Artist] = { ... } // 旧的直接DB查询方法已被删除

  /**
   * [新增] 通过调用 GetArtistByID API 来获取艺术家信息。
   * 这种方式将数据获取的逻辑委托给专门的API，实现了关注点分离。
   * 该方法将API返回的 (Option[Artist], String) 转换为 fail-fast 的 IO[Artist]。
   */
  private def getArtistViaAPI()(using PlanContext): IO[Artist] = {
    logInfo(s"正在通过 GetArtistByID API 查询艺术家: artistID=${artistID}")
    // 管理员作为请求的发起者，使用其身份进行查询
    GetArtistByID(userID = adminID, userToken = adminToken, artistID = artistID).send.flatMap {
      case (Some(artist), _) =>
        // API调用成功且找到了艺术家
        logInfo(s"API查询成功，找到艺术家: ${artist.name}")
        IO.pure(artist)
      case (None, message) =>
        // API调用成功但未找到艺术家，或API调用本身失败并返回了错误信息。
        // 对于当前流程，这两种情况都应视为失败。
        IO.raiseError(new Exception(message))
    }
  }

  /** 检查用户是否已经是该艺术家的管理者。*/
  private def checkNotAlreadyManager(artist: Artist)(using PlanContext): IO[Unit] = {
    logInfo(s"正在检查用户 ${userID} 是否已是艺术家 ${artistID} 的管理者") >> {
      val isAlreadyManager = artist.managedBy.contains(userID)
      if (isAlreadyManager) IO.raiseError(new Exception("该用户已经是此艺术家的管理者"))
      else logInfo(s"用户 ${userID} 不是管理者，可以继续。")
    }
  }

  /** 将新用户ID添加到艺术家的 managedBy 列表并更新数据库。*/
  private def updateArtistManagerList(artist: Artist)(using PlanContext): IO[Unit] = {
    val updatedList = artist.managedBy :+ userID
    val query = s"UPDATE ${schemaName}.artist_table SET managed_by = ? WHERE artist_id = ?"
    logInfo(s"正在更新艺术家 ${artistID} 的管理者列表") >>
      writeDB(
        query,
        List(
          SqlParameter("String", updatedList.asJson.noSpaces),
          SqlParameter("String", artist.artistID)
        )
      ).void
  }

  /** 记录一条带 TraceID 的参考信息日志。*/
  private def logInfo(message: String): IO[Unit] =
    IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))

  /** 记录一条带 TraceID 的错误日志。*/
  private def logError(message: String, cause: Throwable): IO[Unit] =
    IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}