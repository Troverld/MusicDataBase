package Impl

import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import APIs.OrganizeService.validateUserMapping
import APIs.MusicService.GetSongByID
import Utils.StatisticsUtils
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory
import org.joda.time.DateTime

/**
 * Planner for LogPlayback: 记录用户播放歌曲的行为
 *
 * @param userID      播放用户的ID
 * @param userToken   用户认证令牌
 * @param songID      播放的歌曲ID
 * @param planContext 执行上下文
 */
case class LogPlaybackPlanner(
                               userID: String,
                               userToken: String,
                               songID: String,
                               override val planContext: PlanContext
                             ) extends Planner[(Boolean, String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Boolean, String)] = {
    val logic: IO[Unit] = for {
      _ <- logInfo(s"开始记录用户 ${userID} 播放歌曲 ${songID} 的行为")

      // 步骤1: 验证用户身份
      _ <- validateUser()

      // 步骤2: 验证歌曲存在性
      _ <- validateSong()

      // 步骤3: 记录播放行为
      _ <- recordPlayback()

      // 步骤4: 异步更新用户画像缓存（不阻塞主流程）
      _ <- updateUserPortraitAsync()

    } yield ()

    logic.map { _ =>
      (true, "播放记录成功")
    }.handleErrorWith { error =>
      logError(s"用户 ${userID} 播放歌曲 ${songID} 记录失败", error) >>
        IO.pure((false, error.getMessage))
    }
  }

  /**
   * 步骤1: 验证用户身份
   */
  private def validateUser()(using PlanContext): IO[Unit] = {
    logInfo("正在验证用户身份") >> {
      validateUserMapping(userID, userToken).send.flatMap { case (isValid, message) =>
        if (isValid) {
          logInfo("用户身份验证通过")
        } else {
          IO.raiseError(new IllegalArgumentException(s"用户身份验证失败: $message"))
        }
      }
    }
  }

  /**
   * 步骤2: 验证歌曲存在性
   */
  private def validateSong()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证歌曲 ${songID} 是否存在") >> {
      GetSongByID(userID, userToken, songID).send.flatMap { case (songOpt, message) =>
        songOpt match {
          case Some(_) =>
            logInfo("歌曲存在性验证通过")
          case None =>
            IO.raiseError(new IllegalArgumentException(s"歌曲不存在: $message"))
        }
      }
    }
  }

  /**
   * 步骤3: 记录播放行为
   */
  private def recordPlayback()(using PlanContext): IO[Unit] = {
    for {
      logId <- IO(java.util.UUID.randomUUID().toString)
      now = new DateTime()
      _ <- logInfo(s"生成播放记录ID: ${logId}")

      sql = s"INSERT INTO ${schemaName}.playback_log (log_id, user_id, song_id, play_time) VALUES (?, ?, ?, ?)"
      _ <- writeDB(
        sql,
        List(
          SqlParameter("String", logId),
          SqlParameter("String", userID),
          SqlParameter("String", songID),
          SqlParameter("DateTime", now.getMillis.toString)
        )
      )
      _ <- logInfo("播放记录已写入数据库")
    } yield ()
  }

  /**
   * 步骤4: 异步更新用户画像缓存
   * 注意：这里使用fire-and-forget方式，不影响主流程
   */
  private def updateUserPortraitAsync()(using PlanContext): IO[Unit] = {
    logInfo("触发用户画像缓存更新") >> {
      // 使用start来异步执行，不等待完成
      StatisticsUtils.updateUserPortraitCache(userID)
        .handleErrorWith { error =>
          logError("用户画像缓存更新失败", error) >> IO.unit
        }
        .start
        .void
    }
  }

  private def logInfo(message: String): IO[Unit] = 
    IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))
    
  private def logError(message: String, cause: Throwable): IO[Unit] = 
    IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}