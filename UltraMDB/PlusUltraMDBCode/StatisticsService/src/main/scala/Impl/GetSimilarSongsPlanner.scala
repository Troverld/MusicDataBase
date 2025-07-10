// ===== src/main/scala/Impl/GetSimilarSongsPlanner.scala =====

package Impl

import Common.API.{PlanContext, Planner}
import APIs.OrganizeService.validateUserMapping
import APIs.MusicService.GetSongByID
import Utils.GetSimilarSongsUtils
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory

case class GetSimilarSongsPlanner(
                                   userID: String,
                                   userToken: String,
                                   songID: String,
                                   limit: Int,
                                   override val planContext: PlanContext
                                 ) extends Planner[(Option[List[String]], String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Option[List[String]], String)] = {
    val logic: IO[List[String]] = for {
      _ <- logInfo(s"开始查找与歌曲 ${songID} 相似的歌曲，限制数量: ${limit}")
      _ <- validateUser()
      _ <- validateParams()
      // 暂时移除对歌曲的验证
//      _ <- validateTargetSong()
      _ <- logInfo("验证通过，正在调用 GetSimilarSongsUtils 执行查找逻辑")
      similarSongs <- GetSimilarSongsUtils.findSimilarSongs(userID, userToken, songID, limit)
    } yield similarSongs

    logic.map { similarSongs =>
      (Some(similarSongs), "相似歌曲查找成功")
    }.handleErrorWith { error =>
      logError(s"查找歌曲 ${songID} 的相似歌曲失败", error) >>
        IO.pure((None, error.getMessage))
    }
  }

  private def validateUser()(using PlanContext): IO[Unit] = {
    logInfo("正在验证用户身份") >> validateUserMapping(userID, userToken).send.flatMap {
      case (true, _) => logInfo("用户身份验证通过")
      case (false, msg) => IO.raiseError(new IllegalArgumentException(s"用户身份验证失败: $msg"))
    }
  }

  private def validateParams()(using PlanContext): IO[Unit] = {
    logInfo(s"验证参数: limit=${limit}") >> {
      if (limit <= 0 || limit > 100) IO.raiseError(new IllegalArgumentException("相似歌曲数量限制必须在1-100之间"))
      else IO.unit
    }
  }

  private def validateTargetSong()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证目标歌曲 ${songID} 是否存在") >>
      GetSongByID(userID, userToken, songID).send.flatMap {
        case (Some(_), _) => logInfo("目标歌曲存在性验证通过")
        case (None, message) => IO.raiseError(new IllegalArgumentException(s"目标歌曲不存在: $message"))
      }
  }

  private def logInfo(message: String): IO[Unit] =
    IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))

  private def logError(message: String, cause: Throwable): IO[Unit] =
    IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}