package Impl

import Common.API.{PlanContext, Planner}
import APIs.OrganizeService.validateUserMapping
import APIs.CreatorService.{GetArtistByID, GetBandByID}
import Objects.CreatorService.{CreatorID_Type, CreatorType}
import Objects.StatisticsService.Profile
import Utils.GetCreatorGenreStrengthUtils // 导入新的业务逻辑层
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory

/**
 * Planner for GetCreatorGenreStrength: 获取创作者在各曲风下的创作实力。
 * 此 Planner 作为 API 的入口，负责验证和协调，核心业务逻辑已移至 GetCreatorGenreStrengthUtils。
 *
 * @param userID      请求用户的ID
 * @param userToken   用户认证令牌
 * @param creator     创作者的智能ID对象
 * @param planContext 执行上下文
 */
case class GetCreatorGenreStrengthPlanner(
  userID: String,
  userToken: String,
  creator: CreatorID_Type,
  override val planContext: PlanContext
) extends Planner[(Option[Profile], String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Option[Profile], String)] = {
    val logic: IO[Profile] = for {
      _ <- logInfo(s"开始处理获取创作者 ${creator.id} (${creator.creatorType}) 曲风实力的请求")

      // 步骤 1: 执行 API 入口层的验证工作
      _ <- validateUser()
      _ <- validateCreator()

      // 步骤 2: 调用集中的业务逻辑服务来执行核心任务
      _ <- logInfo("验证通过，正在调用 GetCreatorGenreStrengthUtils.generateStrengthProfile")
      strength <- GetCreatorGenreStrengthUtils.generateStrengthProfile(creator, userID, userToken)
      _ <- logInfo(s"实力计算完成，包含 ${strength.vector.length} 个维度")

    } yield strength

    // 步骤 3: 格式化最终的成功或失败响应
    logic.map { strength =>
      (Some(strength), "获取创作实力成功")
    }.handleErrorWith { error =>
      logError(s"获取创作者 ${creator.id} (${creator.creatorType}) 创作实力失败", error) >>
        IO.pure((None, error.getMessage))
    }
  }

  // --- Validation Methods (Planner's Responsibility) ---

  private def validateUser()(using PlanContext): IO[Unit] = {
    logInfo("正在验证用户身份") >>
      validateUserMapping(userID, userToken).send.flatMap {
        case (true, _) => IO.unit
        case (false, message) => IO.raiseError(new IllegalArgumentException(s"用户身份验证失败: $message"))
      }
  }

  private def validateCreator()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证创作者 ${creator.id} 是否存在") >> {
      val validationIO = creator.creatorType match {
        case CreatorType.Artist => GetArtistByID(userID, userToken, creator.id).send
        case CreatorType.Band   => GetBandByID(userID, userToken, creator.id).send
      }
      validationIO.flatMap {
        case (Some(_), _) => logInfo(s"${creator.creatorType}存在性验证通过")
        case (None, msg)  => IO.raiseError(new IllegalStateException(s"${creator.creatorType}不存在: $msg"))
      }
    }
  }

  // --- Logging Helper Methods ---

  private def logInfo(message: String): IO[Unit] =
    IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))

  private def logError(message: String, cause: Throwable): IO[Unit] =
    IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}