package Impl

import Common.API.{PlanContext, Planner}
import APIs.OrganizeService.validateUserMapping
import APIs.CreatorService.{GetArtistByID, GetBandByID}
import Objects.CreatorService.{CreatorID_Type,CreatorType}
import Objects.StatisticsService.Profile
import Utils.GetCreatorCreationTendencyUtils // 导入新的业务逻辑层
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory

/**
 * Planner for GetCreatorCreationTendency: 获取创作者的创作倾向。
 * 此 Planner 作为 API 的入口，负责验证和协调，核心业务逻辑已移至 GetCreatorCreationTendencyUtils。
 *
 * @param userID      请求用户的ID
 * @param userToken   用户认证令牌
 * @param creatorID     创作者的智能ID对象，封装了ID和类型
 * @param planContext 执行上下文
 */
case class GetCreatorCreationTendencyPlanner(
  userID: String,
  userToken: String,
  creatorID: CreatorID_Type,
  override val planContext: PlanContext
) extends Planner[(Option[Profile], String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Option[Profile], String)] = {
    val logic: IO[Profile] = for {
      _ <- logInfo(s"开始处理获取创作者 ${creatorID.id} (${creatorID.creatorType}) 创作倾向的请求")

      // 步骤 1: 执行 API 入口层的验证工作
      _ <- validateUser()

      _ <- validateCreator()

      // 步骤 2: 调用集中的业务逻辑服务来执行核心任务
      _ <- logInfo(s"验证通过，正在调用 GetCreatorCreationTendencyUtils.generateTendencyProfile")
      // 将所有需要的参数传递给业务逻辑层
      tendency <- GetCreatorCreationTendencyUtils.generateTendencyProfile(creatorID, userID, userToken)
      _ <- logInfo(s"倾向计算完成，包含 ${tendency.vector.length} 个维度")

    } yield tendency

    // 步骤 3: 格式化最终的成功或失败响应
    logic.map { tendency =>
      (Some(tendency), "获取创作倾向成功")
    }.handleErrorWith { error =>
      logError(s"获取创作者 ${creatorID.id} (${creatorID.creatorType}) 创作倾向失败", error) >>
        IO.pure((None, error.getMessage))
    }
  }

  /**
   * 验证发起请求的用户身份是否有效。
   */
  private def validateUser()(using PlanContext): IO[Unit] = {
    logInfo("正在验证用户身份") >>
      validateUserMapping(userID, userToken).send.flatMap {
        case (true, _) => logInfo("用户身份验证通过")
        case (false, message) => IO.raiseError(new IllegalArgumentException(s"用户身份验证失败: $message"))
      }
  }

  /**
   * 验证目标创作者是否存在。
   */
  private def validateCreator()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证创作者 ${creatorID.id} 是否存在") >> {
      creatorID.creatorType match {
        case CreatorType.Artist =>
          GetArtistByID(userID, userToken, creatorID.id).send.flatMap {
            case (Some(_), _) => logInfo("艺术家存在性验证通过")
            case (None, message) => IO.raiseError(new IllegalStateException(s"艺术家不存在: $message"))
          }
        case CreatorType.Band =>
          GetBandByID(userID, userToken, creatorID.id).send.flatMap {
            case (Some(_), _) => logInfo("乐队存在性验证通过")
            case (None, message) => IO.raiseError(new IllegalStateException(s"乐队不存在: $message"))
          }
      }
    }
  }

  // 日志记录的辅助方法
  private def logInfo(message: String): IO[Unit] =
    IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))

  private def logError(message: String, cause: Throwable): IO[Unit] =
    IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}