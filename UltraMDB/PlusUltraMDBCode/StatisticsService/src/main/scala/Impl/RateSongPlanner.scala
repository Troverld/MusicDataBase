package Impl

import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import APIs.OrganizeService.validateUserMapping
import APIs.MusicService.GetSongByID
import APIs.StatisticsService.GetSongRate // 1. 导入我们新定义的API
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory
import org.joda.time.DateTime

/**
 * Planner for RateSong: 记录用户对歌曲的评分
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
      _ <- validateRating()
      _ <- validateUser()
      _ <- validateSong()
      _ <- saveRating()
    } yield ()

    logic.map { _ =>
      (true, "评分成功")
    }.handleErrorWith { error =>
      logError(s"用户 ${userID} 对歌曲 ${songID} 评分失败", error) >>
        IO.pure((false, error.getMessage))
    }
  }

  private def validateRating()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证评分值: ${rating}") >> {
      if (rating >= 1 && rating <= 5) {
        logInfo("评分值验证通过")
      } else {
        IO.raiseError(new IllegalArgumentException(s"评分必须在1-5范围内，当前值: ${rating}"))
      }
    }
  }

  private def validateUser()(using PlanContext): IO[Unit] = {
    logInfo("正在验证用户身份") >> {
      validateUserMapping(userID, userToken).send.flatMap {
        case (true, _) => logInfo("用户身份验证通过")
        case (false, message) => IO.raiseError(new IllegalArgumentException(s"用户身份验证失败: $message"))
      }
    }
  }

  private def validateSong()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证歌曲 ${songID} 是否存在") >> {
      GetSongByID(userID, userToken, songID).send.flatMap {
        case (Some(_), _) => logInfo("歌曲存在性验证通过")
        case (None, message) => IO.raiseError(new IllegalArgumentException(s"歌曲不存在: $message"))
      }
    }
  }

  /**
   * 步骤4: 保存或更新评分记录
   */
  private def saveRating()(using PlanContext): IO[Unit] = {
    for {
      now <- IO(new DateTime())
      _ <- logInfo("正在检查用户是否已对该歌曲评过分")
      existingRatingOpt <- checkExistingRating()

      _ <- existingRatingOpt match {
        case Some(oldRating) =>
          logInfo(s"更新现有评分记录，原评分: ${oldRating}，新评分: ${rating}") >>
            updateRating(now)
        case None =>
          logInfo("插入新的评分记录") >>
            insertRating(now)
      }

      _ <- logInfo("评分记录已保存")
    } yield ()
  }

  /**
   * **修正点 1: 使用 GetSongRate API 替代直接数据库访问**
   * 检查是否存在现有评分，封装性更好
   */
  private def checkExistingRating()(using PlanContext): IO[Option[Int]] = {
    GetSongRate(
      userID = this.userID,
      userToken = this.userToken,
      targetUserID = this.userID,
      songID = this.songID
    ).send.flatMap {
      case (ratingValue, _) if ratingValue > 0 => IO.pure(Some(ratingValue))
      case (0, _) => IO.pure(None) // 0 表示未找到评分
      case (errorValue, message) => IO.raiseError(new Exception(s"检查评分时出错 ($errorValue): $message"))
    }
  }

  /**
   * 更新现有评分记录
   */
  private def updateRating(now: DateTime)(using PlanContext): IO[Unit] = {
    val sql = s"UPDATE ${schemaName}.song_rating SET rating = ?, rated_at = ? WHERE user_id = ? AND song_id = ?"
    writeDB(sql, List(
      // **修正点 2: 使用正确的SQL参数类型**
      SqlParameter("Int", rating.toString),
      SqlParameter("DateTime", now.getMillis.toString),
      SqlParameter("String", userID),
      SqlParameter("String", songID)
    )).void
  }

  /**
   * 插入新的评分记录
   */
  private def insertRating(now: DateTime)(using PlanContext): IO[Unit] = {
    val sql = s"INSERT INTO ${schemaName}.song_rating (user_id, song_id, rating, rated_at) VALUES (?, ?, ?, ?)"
    writeDB(sql, List(
      SqlParameter("String", userID),
      SqlParameter("String", songID),
      // **修正点 2: 使用正确的SQL参数类型**
      SqlParameter("Int", rating.toString),
      SqlParameter("DateTime", now.getMillis.toString)
    )).void
  }

  private def logInfo(message: String): IO[Unit] =
    IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))

  private def logError(message: String, cause: Throwable): IO[Unit] =
    IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}