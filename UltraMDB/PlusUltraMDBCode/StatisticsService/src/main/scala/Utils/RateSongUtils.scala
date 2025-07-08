package Utils

import Common.API.PlanContext
import cats.effect.IO
import cats.implicits._
import org.slf4j.LoggerFactory

object RateSongUtils {
  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  /**
   * 核心业务逻辑：保存或更新用户对歌曲的评分。
   * 封装了“读后写”的完整流程。
   */
  def rateSong(userID: String, songID: String, rating: Int)(using planContext: PlanContext): IO[Unit] = {
    for {
      _ <- logInfo(s"在Utils层开始处理评分逻辑，用户: ${userID}, 歌曲: ${songID}")
      
      // 步骤1: 调用DAO层检查是否存在现有评分
      existingRatingOpt <- SearchUtils.fetchUserSongRating(userID, songID)

      // 步骤2: 根据是否存在，决定是更新还是插入
      _ <- existingRatingOpt match {
        case Some(oldRating) =>
          logInfo(s"更新现有评分，原评分: ${oldRating}，新评分: ${rating}") >>
            SearchUtils.updateUserSongRating(userID, songID, rating)
        case None =>
          logInfo("插入新的评分记录") >>
            SearchUtils.insertUserSongRating(userID, songID, rating)
      }
      _ <- logInfo("评分记录已在数据库中保存")
    } yield ()
  }
  
  private def logInfo(message: String)(using pc: PlanContext): IO[Unit] =
    IO(logger.info(s"TID=${pc.traceID.id} -- $message"))
}