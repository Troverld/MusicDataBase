package Impl

import Common.API.{PlanContext, Planner}
import APIs.OrganizeService.validateUserMapping
import APIs.MusicService.GetSongByID
import Utils.RateSongUtils // 导入新的业务逻辑层
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory

/**
 * Planner for RateSong: 记录用户对歌曲的评分。
 * 此 Planner 作为 API 的入口，负责验证和协调，核心的“读后写”业务逻辑已移至 RateSongUtils。
 *
 * @param userID      评分用户的ID
 * @param userToken   用户认证令牌
 * @param songID      被评分的歌曲ID
 * @param rating      用户给出的评分(1-5)
 * @param planContext 执行上下文
 */
case class RateSongPlanner(
  userID: String,
  userToken: String,
  songID: String,
  rating: Int,
  override val planContext: PlanContext
) extends Planner[(Boolean, String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Boolean, String)] = {
    val logic: IO[Unit] = for {
      _ <- logInfo(s"开始处理用户 ${userID} 对歌曲 ${songID} 的评分: ${rating}")
      
      // 步骤 1: 执行 API 入口层的验证工作
      _ <- validateRating()
      _ <- validateUser()
      _ <- validateSong()
      
      // 步骤 2: 调用集中的业务逻辑服务来执行核心的“读后写”操作
      _ <- logInfo("验证通过，正在调用 RateSongUtils.rateSong")
      _ <- RateSongUtils.rateSong(userID, songID, rating)
      _ <- logInfo("评分操作已委托给 RateSongUtils 完成")

    } yield ()

    // 步骤 3: 格式化最终的成功或失败响应
    logic.map { _ =>
      (true, "评分成功")
    }.handleErrorWith { error =>
      logError(s"用户 ${userID} 对歌曲 ${songID} 评分失败", error) >>
        IO.pure((false, error.getMessage))
    }
  }

  private def validateRating()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证评分值: ${rating}") >> {
      if (rating >= 1 && rating <= 5) IO.unit
      else IO.raiseError(new IllegalArgumentException(s"评分必须在1-5范围内，当前值: ${rating}"))
    }
  }

  private def validateUser()(using PlanContext): IO[Unit] = {
    logInfo("正在验证用户身份") >>
      validateUserMapping(userID, userToken).send.flatMap {
        case (true, _) => logInfo("用户身份验证通过")
        case (false, message) => IO.raiseError(new IllegalArgumentException(s"用户身份验证失败: $message"))
      }
  }

  private def validateSong()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证歌曲 ${songID} 是否存在") >>
      GetSongByID(userID, userToken, songID).send.flatMap {
        case (Some(_), _) => logInfo("歌曲存在性验证通过")
        case (None, message) => IO.raiseError(new IllegalArgumentException(s"歌曲不存在: $message"))
      }
  }

  private def logInfo(message: String): IO[Unit] =
    IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))

  private def logError(message: String, cause: Throwable): IO[Unit] =
    IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}