package Impl

import Common.API.{PlanContext, Planner}
import APIs.OrganizeService.validateUserMapping
import Utils.GetUserSongRecommendationsUtils // 导入新的业务逻辑层
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory

/**
 * Planner for GetUserSongRecommendations: 根据用户画像推荐歌曲 (重构版)
 * 此 Planner 作为 API 的入口，负责验证和协调，核心业务逻辑已移至 GetUserSongRecommendationsUtils。
 *
 * @param userID     目标用户的ID
 * @param userToken  用户认证令牌
 * @param pageNumber 页码，从1开始
 * @param pageSize   每页返回的歌曲数量
 * @param planContext 执行上下文
 */
case class GetUserSongRecommendationsPlanner(
                                              userID: String,
                                              userToken: String,
                                              pageNumber: Int = 1,
                                              pageSize: Int = 20,
                                              override val planContext: PlanContext
                                            ) extends Planner[(Option[List[String]], String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Option[List[String]], String)] = {
    val logic: IO[List[String]] = for {
      _ <- logInfo(s"开始处理为用户 ${userID} 推荐歌曲的请求")

      // 步骤 1: 执行 API 入口层的验证工作
      _ <- validateUser()
      _ <- validatePaginationParams()

      // 步骤 2: 调用集中的业务逻辑服务来执行核心任务
      _ <- logInfo("验证通过，正在调用 GetUserSongRecommendationsUtils.generateRecommendations")
      recommendations <- GetUserSongRecommendationsUtils.generateRecommendations(userID, userToken, pageNumber, pageSize)
      _ <- logInfo(s"推荐列表生成完毕，返回 ${recommendations.length} 首歌曲。")

    } yield recommendations

    // 步骤 3: 格式化最终的成功或失败响应
    logic.map { recommendations =>
      (Some(recommendations), "歌曲推荐成功")
    }.handleErrorWith { error =>
      logError(s"为用户 ${userID} 推荐歌曲失败", error) >>
        IO.pure((None, error.getMessage))
    }
  }

  /**
   * 验证发起请求的用户身份是否有效。
   */
  private def validateUser()(using PlanContext): IO[Unit] = {
    logInfo("正在验证用户身份") >> validateUserMapping(userID, userToken).send.flatMap {
      case (true, _) => logInfo("用户身份验证通过")
      case (false, msg) => IO.raiseError(new IllegalArgumentException(s"用户身份验证失败: $msg"))
    }
  }

  /**
   * 验证分页参数的有效性。
   */
  private def validatePaginationParams()(using PlanContext): IO[Unit] = {
    logInfo(s"验证分页参数: pageNumber=${pageNumber}, pageSize=${pageSize}") >> {
      if (pageNumber <= 0) IO.raiseError(new IllegalArgumentException("页码必须大于0"))
      else if (pageSize <= 0 || pageSize > 100) IO.raiseError(new IllegalArgumentException("每页数量必须在1-100之间"))
      else IO.unit
    }
  }

  // 日志记录的辅助方法
  private def logInfo(message: String): IO[Unit] =
    IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))

  private def logError(message: String, cause: Throwable): IO[Unit] =
    IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}