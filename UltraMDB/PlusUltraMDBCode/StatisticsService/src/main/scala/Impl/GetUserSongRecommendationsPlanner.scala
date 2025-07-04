// uncredited

package Impl

import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import APIs.OrganizeService.validateUserMapping
import Utils.StatisticsUtils
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory

/**
 * Planner for GetUserSongRecommendations: 根据用户画像推荐歌曲
 *
 * @param userID     目标用户的ID
 * @param userToken  用户认证令牌
 * @param pageNumber 页码，从1开始
 * @param pageSize   每页返回的歌曲数量
 * @param planContext 执行上下文
 */
case class GetUserSongRecommendationsPlanner(
                                              userID: String,
                                              userToken: String,
                                              pageNumber: Int = 1,
                                              pageSize: Int = 20,
                                              override val planContext: PlanContext
                                            ) extends Planner[(Option[List[String]], String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Option[List[String]], String)] = {
    val logic: IO[List[String]] = for {
      _ <- logInfo(s"开始为用户 ${userID} 推荐歌曲，页码: ${pageNumber}，每页: ${pageSize}")

      // 步骤1: 验证用户身份
      _ <- validateUser()

      // 步骤2: 验证分页参数
      _ <- validatePaginationParams()

      // 步骤3: 获取用户画像
      userPortrait <- getUserPortrait()

      // 步骤4: 基于用户画像推荐歌曲
      recommendations <- if (userPortrait.vector.isEmpty) {
        logInfo("用户暂无画像数据，推荐热门歌曲") >>
        getPopularSongs()
      } else {
        logInfo("基于用户画像进行个性化推荐") >>
        getPersonalizedRecommendations(userPortrait.vector)
      }

      // 步骤5: 分页处理
      paginatedResults = paginate(recommendations)
      _ <- logInfo(s"推荐完成，返回 ${paginatedResults.length} 首歌曲")

    } yield paginatedResults

    logic.map { recommendations =>
      (Some(recommendations), "歌曲推荐成功")
    }.handleErrorWith { error =>
      logError(s"为用户 ${userID} 推荐歌曲失败", error) >>
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
   * 步骤2: 验证分页参数
   */
  private def validatePaginationParams()(using PlanContext): IO[Unit] = {
    logInfo(s"验证分页参数: pageNumber=${pageNumber}, pageSize=${pageSize}") >> {
      if (pageNumber <= 0) {
        IO.raiseError(new IllegalArgumentException("页码必须大于0"))
      } else if (pageSize <= 0 || pageSize > 100) {
        IO.raiseError(new IllegalArgumentException("每页数量必须在1-100之间"))
      } else {
        logInfo("分页参数验证通过")
      }
    }
  }

  /**
   * 步骤3: 获取用户画像
   */
  private def getUserPortrait()(using PlanContext): IO[Objects.StatisticsService.Profile] = {
    for {
      _ <- logInfo("获取用户画像")
      cachedPortrait <- StatisticsUtils.getUserPortraitFromCache(userID)
      portrait <- cachedPortrait match {
        case Some(portrait) =>
          logInfo("从缓存获取用户画像") >> IO.pure(portrait)
        case None =>
          logInfo("缓存中无用户画像，实时计算") >>
          StatisticsUtils.calculateUserPortrait(userID).map { vector =>
            Objects.StatisticsService.Profile(vector, norm = true)
          }
      }
    } yield portrait
  }

  /**
   * 步骤4a: 获取热门歌曲（当用户无画像时）
   */
  private def getPopularSongs()(using PlanContext): IO[List[String]] = {
    // 获取最近播放次数最多的歌曲
    val sql = s"""
      SELECT pl.song_id, COUNT(*) as play_count
      FROM ${schemaName}.playback_log pl
      WHERE pl.play_time > (EXTRACT(EPOCH FROM NOW()) * 1000 - 7 * 24 * 3600 * 1000) -- 最近7天
      GROUP BY pl.song_id
      ORDER BY play_count DESC
      LIMIT ?
    """
    
    val totalNeeded = pageNumber * pageSize
    readDBRows(sql, List(SqlParameter("String", totalNeeded.toString))).map { rows =>
      rows.map(row => decodeField[String](row, "song_id"))
    }
  }

  /**
   * 步骤4b: 基于用户画像的个性化推荐
   */
  private def getPersonalizedRecommendations(userPreferences: List[(String, Double)])(using PlanContext): IO[List[String]] = {
    for {
      // 获取用户已播放的歌曲，避免重复推荐
      playedSongs <- getUserPlayedSongs()
      _ <- logInfo(s"用户已播放歌曲 ${playedSongs.length} 首，将从推荐中排除")
      
      // 根据用户偏好的曲风推荐歌曲
      recommendations <- getRecommendationsByPreferences(userPreferences, playedSongs)
      
    } yield recommendations
  }

  /**
   * 获取用户已播放的歌曲列表
   */
  private def getUserPlayedSongs()(using PlanContext): IO[Set[String]] = {
    val sql = s"SELECT DISTINCT song_id FROM ${schemaName}.playback_log WHERE user_id = ?"
    readDBRows(sql, List(SqlParameter("String", userID))).map { rows =>
      rows.map(row => decodeField[String](row, "song_id")).toSet
    }
  }

  /**
   * 根据用户偏好推荐歌曲
   */
  private def getRecommendationsByPreferences(
    preferences: List[(String, Double)],
    excludeSongs: Set[String]
  )(using PlanContext): IO[List[String]] = {
    // 按偏好度排序，优先推荐用户喜欢的曲风
    val sortedPreferences = preferences.sortBy(-_._2).take(5) // 取前5个偏好曲风
    
    for {
      // 为每个偏好曲风获取推荐歌曲
      genreRecommendations <- sortedPreferences.traverse { case (genreId, preference) =>
        getGenreRecommendations(genreId, preference, excludeSongs)
      }
      
      // 合并并打乱推荐结果
      allRecommendations = genreRecommendations.flatten
      shuffledRecommendations = scala.util.Random.shuffle(allRecommendations)
      
      // 确保有足够的推荐数量
      finalRecommendations <- if (shuffledRecommendations.length < pageNumber * pageSize) {
        // 如果推荐数量不足，补充一些热门歌曲
        supplementWithPopularSongs(shuffledRecommendations, excludeSongs)
      } else {
        IO.pure(shuffledRecommendations)
      }
      
    } yield finalRecommendations
  }

  /**
   * 获取特定曲风的推荐歌曲
   */
  private def getGenreRecommendations(
    genreId: String,
    preference: Double,
    excludeSongs: Set[String]
  )(using PlanContext): IO[List[String]] = {
    // 根据偏好度决定推荐数量
    val recommendCount = Math.max(1, (preference * 20).toInt)
    
    val sql = s"""
      SELECT s.song_id, COALESCE(spc.popularity_score, 0) as popularity
      FROM ${schemaName}.song_genre_mapping sgm
      JOIN ${schemaName}.song_table s ON sgm.song_id = s.song_id
      LEFT JOIN ${schemaName}.song_popularity_cache spc ON s.song_id = spc.song_id
      WHERE sgm.genre_id = ?
      ORDER BY popularity DESC
      LIMIT ?
    """
    
    readDBRows(sql, List(
      SqlParameter("String", genreId),
      SqlParameter("String", (recommendCount * 2).toString) // 多获取一些以便过滤
    )).map { rows =>
      rows.map(row => decodeField[String](row, "song_id"))
        .filterNot(excludeSongs.contains)
        .take(recommendCount)
    }
  }

  /**
   * 用热门歌曲补充推荐列表
   */
  private def supplementWithPopularSongs(
    currentRecommendations: List[String],
    excludeSongs: Set[String]
  )(using PlanContext): IO[List[String]] = {
    val needed = pageNumber * pageSize - currentRecommendations.length
    if (needed <= 0) {
      IO.pure(currentRecommendations)
    } else {
      val allExclude = excludeSongs ++ currentRecommendations.toSet
      
      val sql = s"""
        SELECT s.song_id
        FROM ${schemaName}.song_table s
        LEFT JOIN ${schemaName}.song_popularity_cache spc ON s.song_id = spc.song_id
        ORDER BY COALESCE(spc.popularity_score, 0) DESC
        LIMIT ?
      """
      
      readDBRows(sql, List(SqlParameter("String", (needed * 2).toString))).map { rows =>
        val popularSongs = rows.map(row => decodeField[String](row, "song_id"))
          .filterNot(allExclude.contains)
          .take(needed)
        currentRecommendations ++ popularSongs
      }
    }
  }

  /**
   * 步骤5: 分页处理
   */
  private def paginate(recommendations: List[String]): List[String] = {
    val startIndex = (pageNumber - 1) * pageSize
    val endIndex = startIndex + pageSize
    
    if (startIndex >= recommendations.length) {
      List.empty
    } else {
      recommendations.slice(startIndex, Math.min(endIndex, recommendations.length))
    }
  }

  private def logInfo(message: String): IO[Unit] = 
    IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))
    
  private def logError(message: String, cause: Throwable): IO[Unit] = 
    IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}