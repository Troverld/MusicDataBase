// uncredited

package Impl

import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import APIs.OrganizeService.validateUserMapping
import APIs.MusicService.GetSongByID
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

      // 步骤1: 验证评分范围
      _ <- validateRating()

      // 步骤2: 验证用户身份
      _ <- validateUser()

      // 步骤3: 验证歌曲存在性
      _ <- validateSong()

      // 步骤4: 保存或更新评分记录
      _ <- saveRating()

    } yield ()

    logic.map { _ =>
      (true, "评分成功")
    }.handleErrorWith { error =>
      logError(s"用户 ${userID} 对歌曲 ${songID} 评分失败", error) >>
        IO.pure((false, error.getMessage))
    }
  }

  /**
   * 步骤1: 验证评分范围
   */
  private def validateRating()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证评分值: ${rating}") >> {
      if (rating >= 1 && rating <= 5) {
        logInfo("评分值验证通过")
      } else {
        IO.raiseError(new IllegalArgumentException(s"评分必须在1-5范围内，当前值: ${rating}"))
      }
    }
  }

  /**
   * 步骤2: 验证用户身份
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
   * 步骤3: 验证歌曲存在性
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
   * 步骤4: 保存或更新评分记录
   * 使用 UPSERT 操作，如果用户已经对该歌曲评分则更新，否则插入新记录
   */
  private def saveRating()(using PlanContext): IO[Unit] = {
    for {
      now = new DateTime()
      _ <- logInfo("准备保存评分记录")

      // 检查是否已存在评分记录
      existingRating <- checkExistingRating()
      
      _ <- existingRating match {
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
   * 检查是否存在现有评分
   */
  private def checkExistingRating()(using PlanContext): IO[Option[Int]] = {
    val sql = s"SELECT rating FROM ${schemaName}.song_rating WHERE user_id = ? AND song_id = ?"
    readDBRows(sql, List(
      SqlParameter("String", userID),
      SqlParameter("String", songID)
    )).map { rows =>
      rows.headOption.map(row => decodeField[Int](row, "rating"))
    }
  }

  /**
   * 更新现有评分记录
   */
  private def updateRating(now: DateTime)(using PlanContext): IO[Unit] = {
    val sql = s"UPDATE ${schemaName}.song_rating SET rating = ?, rated_at = ? WHERE user_id = ? AND song_id = ?"
    writeDB(sql, List(
      SqlParameter("String", rating.toString),
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
      SqlParameter("String", rating.toString),
      SqlParameter("DateTime", now.getMillis.toString)
    )).void
  }

  private def logInfo(message: String): IO[Unit] = 
    IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))
    
  private def logError(message: String, cause: Throwable): IO[Unit] = 
    IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}