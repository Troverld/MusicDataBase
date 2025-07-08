package Impl

import Common.API.{PlanContext, Planner}
import APIs.OrganizeService.validateUserMapping
import Utils.SearchUtils // 导入 SearchUtils 以复用其数据库访问方法
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory

/**
 * Planner for GetSongRate: 查询指定用户对某首歌曲的评分。
 * 此 Planner 作为 API 的入口，负责验证和协调，核心的数据库查询操作已委托给 SearchUtils。
 *
 * @param userID       发起请求的用户ID
 * @param userToken    发起请求的用户令牌
 * @param targetUserID 被查询评分的目标用户ID
 * @param songID       被查询评分的歌曲ID
 * @param planContext  执行上下文
 */
case class GetSongRatePlanner(
                               userID: String,
                               userToken: String,
                               targetUserID: String,
                               songID: String,
                               override val planContext: PlanContext
                             ) extends Planner[(Int, String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Int, String)] = {
    val logic: IO[Int] = for {
      _ <- logInfo(s"开始处理查询用户 ${targetUserID} 对歌曲 ${songID} 评分的请求")

      // 步骤 1: 执行 API 入口层的验证工作
      _ <- validateUser()

      // 步骤 2: 调用集中的数据访问服务来执行核心的查询操作
      _ <- logInfo(s"验证通过，正在调用 SearchUtils.fetchUserSongRating")
      ratingOpt <- SearchUtils.fetchUserSongRating(targetUserID, songID)

      // 步骤 3: 处理查询结果
      rating = ratingOpt.getOrElse(0) // 如果未找到(None)，则评分为0
      _ <- logInfo(s"查询完成，评分为: $rating")

    } yield rating

    // 步骤 4: 格式化最终的成功或失败响应
    logic.map { rating =>
      val message = if (rating > 0) "查询评分成功" else "用户未对该歌曲评分"
      (rating, message)
    }.handleErrorWith { error =>
      logError(s"查询用户 ${targetUserID} 对歌曲 ${songID} 的评分失败", error) >>
        IO.pure((-1, error.getMessage)) // -1 表示查询过程中发生错误
    }
  }

  /**
   * 验证发起请求的用户身份是否有效。
   */
  private def validateUser()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证调用者 ${userID} 的身份") >>
      validateUserMapping(userID, userToken).send.flatMap {
        case (true, _) => logInfo("调用者身份验证通过")
        case (false, message) => IO.raiseError(new IllegalArgumentException(s"调用者身份验证失败: $message"))
      }
  }

  private def logInfo(message: String): IO[Unit] =
    IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))

  private def logError(message: String, cause: Throwable): IO[Unit] =
    IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}