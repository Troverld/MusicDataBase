// ===== src/main/scala/Impl/GetNextSongRecommendationPlanner.scala =====

package Impl

import Common.API.{PlanContext, Planner}
import APIs.OrganizeService.validateUserMapping
import APIs.MusicService.GetSongByID
import Utils.GetNextSongRecommendationUtils
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory

case class GetNextSongRecommendationPlanner(
                                             userID: String,
                                             userToken: String,
                                             currentSongID: String,
                                             override val planContext: PlanContext
                                           ) extends Planner[(Option[String], String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Option[String], String)] = {
    val logic: IO[String] = for {
      _ <- logInfo(s"开始为用户 ${userID} 基于当前歌曲 ${currentSongID} 推荐下一首歌")
      _ <- validateUser()
      _ <- validateCurrentSong()
      _ <- logInfo("验证通过，正在调用 GetNextSongRecommendationUtils 执行推荐逻辑")
      nextSongId <- GetNextSongRecommendationUtils.generateNextSongRecommendation(userID, userToken, currentSongID)
      _ <- logInfo(s"推荐逻辑执行完毕，推荐歌曲ID: ${nextSongId}")
    } yield nextSongId

    logic.map { songId =>
      (Some(songId), "下一首歌推荐成功")
    }.handleErrorWith { error =>
      logError(s"为用户 ${userID} 推荐下一首歌失败", error) >> IO.pure((None, error.getMessage))
    }
  }

  private def validateUser()(using PlanContext): IO[Unit] = {
    logInfo("正在验证用户身份") >>
      validateUserMapping(userID, userToken).send.flatMap {
        case (true, _) => logInfo("用户身份验证通过")
        case (false, message) => IO.raiseError(new IllegalArgumentException(s"用户身份验证失败: $message"))
      }
  }

  private def validateCurrentSong()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证当前歌曲 ${currentSongID} 是否存在") >>
      GetSongByID(userID, userToken, currentSongID).send.flatMap {
        case (Some(_), _) => logInfo("当前歌曲存在性验证通过")
        case (None, message) => IO.raiseError(new IllegalStateException(s"当前歌曲不存在: $message"))
      }
  }

  private def logInfo(message: String): IO[Unit] =
    IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))

  private def logError(message: String, cause: Throwable): IO[Unit] =
    IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}