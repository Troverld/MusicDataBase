package Utils

import Common.API.PlanContext
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import Objects.StatisticsService.Profile
import cats.effect.IO
import org.joda.time.DateTime
import scala.math.sqrt

object StatisticsUtils {

  /**
   * 计算两个向量的余弦相似度
   * @param vectorA 向量A，格式为 List[(String, Double)]
   * @param vectorB 向量B，格式为 List[(String, Double)]
   * @return 余弦相似度值，范围 [0, 1]
   */
  def calculateCosineSimilarity(vectorA: List[(String, Double)], vectorB: List[(String, Double)]): Double = {
    val mapA = vectorA.toMap
    val mapB = vectorB.toMap
    val commonKeys = mapA.keySet.intersect(mapB.keySet)
    
    if (commonKeys.isEmpty) return 0.0
    
    val dotProduct = commonKeys.map(key => mapA(key) * mapB(key)).sum
    val magnitudeA = sqrt(mapA.values.map(v => v * v).sum)
    val magnitudeB = sqrt(mapB.values.map(v => v * v).sum)
    
    if (magnitudeA == 0.0 || magnitudeB == 0.0) 0.0
    else dotProduct / (magnitudeA * magnitudeB)
  }

  /**
   * 归一化向量，使所有值的和为1.0
   * @param vector 待归一化的向量
   * @return 归一化后的向量
   */
  def normalizeVector(vector: List[(String, Double)]): List[(String, Double)] = {
    val sum = vector.map(_._2).sum
    if (sum == 0.0) vector
    else vector.map { case (key, value) => (key, value / sum) }
  }

  /**
   * 实时计算用户画像
   * @param userId 用户ID
   * @param planContext 执行上下文
   * @return 用户画像向量
   */
  def calculateUserPortrait(userId: String)(using planContext: PlanContext): IO[List[(String, Double)]] = {
    for {
      // 获取用户的播放记录，按曲风分组统计
      playbackStats <- getPlaybackStatsByGenre(userId)
      // 获取用户的评分记录
      ratingStats <- getRatingStatsByGenre(userId)
      // 合并播放和评分数据，计算综合偏好度
      combinedStats = combinePlaybackAndRating(playbackStats, ratingStats)
      // 归一化处理
      normalizedStats = normalizeVector(combinedStats)
    } yield normalizedStats
  }

  /**
   * 获取用户按曲风分组的播放统计
   */
  private def getPlaybackStatsByGenre(userId: String)(using PlanContext): IO[List[(String, Double)]] = {
    val sql = s"""
      SELECT sgm.genre_id, COUNT(*) as play_count
      FROM ${schemaName}.playback_log pl
      JOIN ${schemaName}.song_genre_mapping sgm ON pl.song_id = sgm.song_id  
      WHERE pl.user_id = ?
      GROUP BY sgm.genre_id
    """
    
    readDBRows(sql, List(SqlParameter("String", userId))).map { rows =>
      rows.map { row =>
        val genreId = decodeField[String](row, "genre_id")
        val playCount = decodeField[Int](row, "play_count").toDouble
        (genreId, playCount)
      }
    }
  }

  /**
   * 获取用户按曲风分组的评分统计
   */
  private def getRatingStatsByGenre(userId: String)(using PlanContext): IO[Map[String, Double]] = {
    val sql = s"""
      SELECT sgm.genre_id, AVG(sr.rating) as avg_rating, COUNT(*) as rating_count
      FROM ${schemaName}.song_rating sr
      JOIN ${schemaName}.song_genre_mapping sgm ON sr.song_id = sgm.song_id
      WHERE sr.user_id = ?
      GROUP BY sgm.genre_id
    """
    
    readDBRows(sql, List(SqlParameter("String", userId))).map { rows =>
      rows.map { row =>
        val genreId = decodeField[String](row, "genre_id")
        val avgRating = decodeField[Double](row, "avg_rating")
        (genreId, avgRating)
      }.toMap
    }
  }

  /**
   * 合并播放次数和评分数据
   */
  private def combinePlaybackAndRating(
    playbackStats: List[(String, Double)], 
    ratingStats: Map[String, Double]
  ): List[(String, Double)] = {
    playbackStats.map { case (genreId, playCount) =>
      val ratingWeight = ratingStats.get(genreId).map(_ / 5.0).getOrElse(1.0) // 默认权重为1.0
      val combinedScore = playCount * ratingWeight
      (genreId, combinedScore)
    }
  }

  /**
   * 计算歌曲热度
   * @param songId 歌曲ID
   * @return 热度分数
   */
  def calculateSongPopularity(songId: String)(using PlanContext): IO[Double] = {
    for {
      playCount <- getPlayCount(songId)
      (avgRating, ratingCount) <- getRatingStats(songId)
      popularity = playCount * 0.7 + avgRating * ratingCount * 0.3
    } yield popularity
  }

  /**
   * 获取歌曲播放次数
   */
  private def getPlayCount(songId: String)(using PlanContext): IO[Int] = {
    val sql = s"SELECT COUNT(*) FROM ${schemaName}.playback_log WHERE song_id = ?"
    readDBInt(sql, List(SqlParameter("String", songId)))
  }

  /**
   * 获取歌曲评分统计
   */
  private def getRatingStats(songId: String)(using PlanContext): IO[(Double, Int)] = {
    val sql = s"SELECT AVG(rating), COUNT(*) FROM ${schemaName}.song_rating WHERE song_id = ?"
    readDBRows(sql, List(SqlParameter("String", songId))).map { rows =>
      rows.headOption.map { row =>
        val avgRating = Option(decodeField[Double](row, "avg")).getOrElse(0.0)
        val count = decodeField[Int](row, "count")
        (avgRating, count)
      }.getOrElse((0.0, 0))
    }
  }

  /**
   * 更新用户画像缓存
   */
  def updateUserPortraitCache(userId: String)(using PlanContext): IO[Unit] = {
    for {
      portrait <- calculateUserPortrait(userId)
      now = new DateTime()
      // 先删除旧缓存
      _ <- writeDB(
        s"DELETE FROM ${schemaName}.user_portrait_cache WHERE user_id = ?",
        List(SqlParameter("String", userId))
      )
      // 插入新缓存
      _ <- portrait.traverse { case (genreId, score) =>
        writeDB(
          s"INSERT INTO ${schemaName}.user_portrait_cache (user_id, genre_id, preference_score, updated_at) VALUES (?, ?, ?, ?)",
          List(
            SqlParameter("String", userId),
            SqlParameter("String", genreId),
            SqlParameter("String", score.toString),
            SqlParameter("DateTime", now.getMillis.toString)
          )
        )
      }
    } yield ()
  }

  /**
   * 从缓存获取用户画像
   */
  def getUserPortraitFromCache(userId: String)(using PlanContext): IO[Option[Profile]] = {
    val sql = s"SELECT genre_id, preference_score FROM ${schemaName}.user_portrait_cache WHERE user_id = ?"
    readDBRows(sql, List(SqlParameter("String", userId))).map { rows =>
      if (rows.nonEmpty) {
        val vector = rows.map { row =>
          val genreId = decodeField[String](row, "genre_id")
          val score = decodeField[Double](row, "preference_score")
          (genreId, score)
        }
        Some(Profile(vector, norm = true))
      } else {
        None
      }
    }
  }

  /**
   * 检查缓存是否过期（超过1小时）
   */
  def isCacheExpired(userId: String)(using PlanContext): IO[Boolean] = {
    val sql = s"SELECT MAX(updated_at) FROM ${schemaName}.user_portrait_cache WHERE user_id = ?"
    readDBRows(sql, List(SqlParameter("String", userId))).map { rows =>
      rows.headOption.map { row =>
        val lastUpdated = decodeField[Long](row, "max")
        val now = new DateTime().getMillis
        (now - lastUpdated) > 3600000 // 1小时 = 3600000毫秒
      }.getOrElse(true) // 如果没有缓存，认为已过期
    }
  }
}