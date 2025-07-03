package Impl

// 外部服务API的导入
import APIs.OrganizeService.validateUserMapping // API for user authentication

// 内部项目通用库的导入
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import Objects.CreatorService.Band // 导入 Band 定义

// 第三方库的导入
import cats.effect.IO
import cats.implicits._
// 保留这个导入，因为 Planner 可能会隐式地依赖它
import io.circe.generic.auto._ 
// 显式导入我们需要的东西
import io.circe.{Json, DecodingFailure}
import io.circe.parser.parse
import org.slf4j.LoggerFactory

case class GetBandByIDPlanner(
  userID: String,
  userToken: String,
  bandID: String,
  override val planContext: PlanContext
) extends Planner[(Option[Band], String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Option[Band], String)] = {
    val logic: IO[(Option[Band], String)] = for {
      // 1. 用户认证
      _ <- validateUser()

      // 2. 从数据库获取并处理乐队信息
      result <- fetchAndProcessBand()
    } yield result

    // 统一错误处理
    logic.handleErrorWith { error =>
      logError(s"查询乐队 ${bandID} 的操作失败", error) >>
        IO.pure((None, s"查询乐队失败: ${error.getMessage}"))
    }
  }

  /** 验证用户身份 */
  private def validateUser()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证用户身份: userID=${userID}")
    validateUserMapping(userID, userToken).send.flatMap {
      case (true, _) => logInfo("用户验证通过。")
      case (false, errorMsg) => IO.raiseError(new Exception(s"用户认证失败: $errorMsg"))
    }
  }

  /**
   * 从数据库中获取乐队信息，并对返回的原始 JSON 进行预处理
   */
  private def fetchAndProcessBand()(using PlanContext): IO[(Option[Band], String)] = {
    logInfo(s"正在数据库中查询乐队: band_id = ${bandID}")
    val query = s"""SELECT * FROM "${schemaName}"."band_table" WHERE band_id = ?"""
    val params = List(SqlParameter("String", bandID))

    // 注意：这里我们假设 readDBRows 返回 List[Json]，如果不是，需要用 readDBJsonOptional
    // 根据你的 Planner 代码，readDBRows 应该是正确的
    readDBRows(query, params).flatMap {
      case rawJson :: Nil => // 匹配到一行数据
        // 对 "members" 字段进行预处理，将其从 "双重编码的字符串" 修复为真正的 JSON 数组
        val patchedJson = rawJson.mapObject { jsonObj =>
          jsonObj("members") match {
            // 检查 'members' 字段是否存在且为 JSON 字符串
            case Some(jsonVal) if jsonVal.isString =>
              // 安全地获取字符串值，如果为空则默认为 "[]"
              val jsonStr = jsonVal.asString.getOrElse("[]")
              // 尝试将这个字符串解析成一个 JSON 对象（这里应该是一个数组）
              parse(jsonStr) match {
                // 如果解析成功且结果是一个数组
                case Right(arrayJson) if arrayJson.isArray =>
                  // 用解析后的 JSON 数组替换掉原来的字符串
                  jsonObj.add("members", arrayJson)
                // 如果解析失败或结果不是数组，则保持原样（或可以移除该字段）
                case _ => jsonObj 
              }
            // 如果 'members' 字段不是字符串或不存在，则不进行任何操作
            case _ => jsonObj
          }
        }
        
        // 使用修复后的 patchedJson 进行解码
        // 这里的 .as[Band] 会自动使用隐式解码器
        IO.fromEither(patchedJson.as[Band])
          .map(band => (Some(band), "查询乐队成功")) // 成功则包装
          .handleErrorWith(err => {
            logError(s"对修复后的JSON解码失败: ${patchedJson.noSpaces}", err)
            IO.pure((None, s"解码Band对象失败: ${err.getMessage}"))
          })

      case Nil =>
        // 未找到乐队
        IO.pure((None, s"未找到ID为 ${bandID} 的乐队"))

      case _ =>
        // 数据库中存在重复的主键
        IO.raiseError(new Exception(s"数据库主键冲突: 存在多个ID为 ${bandID} 的乐队"))
    }
  }

  /** 记录日志 */
  private def logInfo(message: String): IO[Unit] = IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))
  private def logError(message: String, cause: Throwable): IO[Unit] = IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}