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
 * Planner for GetSongPopularity: 获取歌曲的热度分数
 *
 * @param userID      请求用户的ID
 * @param userToken   用户认证令牌
 * @param songID      要查询热度的歌曲ID
 * @param planContext 执行上下文
 */
case class GetSongPopularityPlanner(
                                     userID: String,
                                     userToken: String,
                                     songID: String,
                                     override val planContext: PlanContext
                                   ) extends Planner[(Option[Double], String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Option[Double], String)] = {
    val logic: IO[Double] = for {
      _ <- logInfo(s"开始获取歌曲 ${songID} 的热度分数")

      // 步骤1: 验证用户身份
      _ <- validateUser()

      // 步骤2: 验证歌曲存在性
      _ <- validateSong()

      // 步骤3: 尝试从缓存获取热度
      cachedPopularity <- tryGetFromCache()

      // 步骤4: 如果缓存不存在或过期，实时计算热度
      popularity <- cachedPopularity match {
        case Some(popularity) =>
          logInfo(s"从缓存获取歌曲热度: ${popularity}") >> IO.pure(popularity)
        case None =>
          logInfo("缓存不存在或已过期，开始实时计算热度") >>
          calculateAndCachePopularity()
      }

    } yield popularity

    logic.map { popularity =>
      (Some(popularity), "获取歌曲热度成功")
    }.handleErrorWith { error =>
      logError(s"获取歌曲 ${songID} 热度失败", error) >>
        IO.pure((None, error.getMessage))
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
   * 步骤3: 尝试从缓存获取热度
   */
  private def tryGetFromCache()(using PlanContext): IO[Option[Double]] = {
    for {
      _ <- logInfo("检查歌曲热度缓存")
      isExpired <- isPopularityCacheExpired()
      popularity <- if (isExpired) {
        logInfo("缓存已过期或不存在")
        IO.pure(None)
      } else {
        logInfo("缓存有效，尝试获取")
        getPopularityFromCache()
      }
    } yield popularity
  }

  /**
   * 检查热度缓存是否过期（超过30分钟）
   */
  private def isPopularityCacheExpired()(using PlanContext): IO[Boolean] = {
    val sql = s"SELECT updated_at FROM ${schemaName}.song_popularity_cache WHERE song_id = ?"
    readDBRows(sql, List(SqlParameter("String", songID))).map { rows =>
      rows.headOption.map { row =>
        val lastUpdated = decodeField[Long](row, "updated_at")
        val now = new DateTime().getMillis
        (now - lastUpdated) > 1800000 // 30分钟 = 1800000毫秒
      }.getOrElse(true) // 如果没有缓存，认为已过期
    }
  }

  /**
   * 从缓存获取热度分数
   */
  private def getPopularityFromCache()(using PlanContext): IO[Option[Double]] = {
    val sql = s"SELECT popularity_score FROM ${schemaName}.song_popularity_cache WHERE song_id = ?"
    readDBRows(sql, List(SqlParameter("String", songID))).map { rows =>
      rows.headOption.map(row => decodeField[Double](row, "popularity_score"))
    }
  }

  /**
   * 步骤4: 实时计算热度并更新缓存
   */
  private def calculateAndCachePopularity()(using PlanContext): IO[Double] = {
    for {
      _ <- logInfo("开始实时计算歌曲热度")
      
      // 获取播放统计
      playCount <- getPlayCount()
      _ <- logInfo(s"歌曲播放次数: ${playCount}")
      
      // 获取评分统计
      (avgRating, ratingCount) <- getRatingStats()
      _ <- logInfo(s"歌曲平均评分: ${avgRating}，评分人数: ${ratingCount}")
      
      // 计算热度: 播放次数 * 0.7 + 平均评分 * 评分人数 * 0.3
      popularity = playCount * 0.7 + avgRating * ratingCount * 0.3
      _ <- logInfo(s"计算得出热度分数: ${popularity}")
      
      // 更新缓存
      _ <- updatePopularityCache(popularity, playCount, avgRating, ratingCount)
      _ <- logInfo("热度缓存已更新")
      
    } yield popularity
  }

  /**
   * 获取歌曲播放次数
   */
  private def getPlayCount()(using PlanContext): IO[Int] = {
    val sql = s"SELECT COUNT(*) FROM ${schemaName}.playback_log WHERE song_id = ?"
    readDBInt(sql, List(SqlParameter("String", songID)))
  }

  /**
   * 获取歌曲评分统计
   */
  private def getRatingStats()(using PlanContext): IO[(Double, Int)] = {
    val sql = s"SELECT AVG(rating), COUNT(*) FROM ${schemaName}.song_rating WHERE song_id = ?"
    readDBRows(sql, List(SqlParameter("String", songID))).map { rows =>
      rows.headOption.map { row =>
        val avgRating = Option(decodeField[Double](row, "avg")).getOrElse(0.0)
        val count = decodeField[Int](row, "count")
        (avgRating, count)
      }.getOrElse((0.0, 0))
    }
  }

  /**
   * 更新热度缓存
   */
  private def updatePopularityCache(
    popularity: Double,
    playCount: Int,
    avgRating: Double,
    ratingCount: Int
  )(using PlanContext): IO[Unit] = {
    val now = new DateTime()
    
    // 先尝试更新
    val updateSql = s"""
      UPDATE ${schemaName}.song_popularity_cache 
      SET popularity_score = ?, play_count = ?, avg_rating = ?, rating_count = ?, updated_at = ?
      WHERE song_id = ?
    """
    
    for {
      updateResult <- writeDB(updateSql, List(
        SqlParameter("String", popularity.toString),
        SqlParameter("String", playCount.toString),
        SqlParameter("String", avgRating.toString),
        SqlParameter("String", ratingCount.toString),
        SqlParameter("DateTime", now.getMillis.toString),
        SqlParameter("String", songID)
      ))
      
      // 如果更新失败（记录不存在），则插入新记录
      _ <- if (updateResult.contains("0")) {
        val insertSql = s"""
          INSERT INTO ${schemaName}.song_popularity_cache 
          (song_id, popularity_score, play_count, avg_rating, rating_count, updated_at)
          VALUES (?, ?, ?, ?, ?, ?)
        """
        writeDB(insertSql, List(
          SqlParameter("String", songID),
          SqlParameter("String", popularity.toString),
          SqlParameter("String", playCount.toString),
          SqlParameter("String", avgRating.toString),
          SqlParameter("String", ratingCount.toString),
          SqlParameter("DateTime", now.getMillis.toString)
        ))
      } else {
        IO.unit
      }
    } yield ()
  }

  private def logInfo(message: String): IO[Unit] = 
    IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))
    
  private def logError(message: String, cause: Throwable): IO[Unit] = 
    IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}