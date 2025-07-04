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

/**
 * Planner for GetSimilarSongs: 获取与指定歌曲相似的歌曲列表
 *
 * @param userID      用户ID
 * @param userToken   用户认证令牌
 * @param songID      目标歌曲ID
 * @param limit       返回的相似歌曲数量
 * @param planContext 执行上下文
 */
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

      // 步骤1: 验证用户身份
      _ <- validateUser()

      // 步骤2: 验证参数
      _ <- validateParams()

      // 步骤3: 验证目标歌曲存在性
      targetSong <- validateAndGetTargetSong()

      // 步骤4: 获取目标歌曲的特征信息
      songFeatures <- getSongFeatures(targetSong)

      // 步骤5: 查找相似歌曲
      similarSongs <- findSimilarSongs(songFeatures)

      _ <- logInfo(s"找到 ${similarSongs.length} 首相似歌曲")

    } yield similarSongs

    logic.map { similarSongs =>
      (Some(similarSongs), "相似歌曲查找成功")
    }.handleErrorWith { error =>
      logError(s"查找歌曲 ${songID} 的相似歌曲失败", error) >>
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
   * 步骤2: 验证参数
   */
  private def validateParams()(using PlanContext): IO[Unit] = {
    logInfo(s"验证参数: limit=${limit}") >> {
      if (limit <= 0 || limit > 100) {
        IO.raiseError(new IllegalArgumentException("相似歌曲数量限制必须在1-100之间"))
      } else {
        logInfo("参数验证通过")
      }
    }
  }

  /**
   * 步骤3: 验证目标歌曲存在性并获取歌曲信息
   */
  private def validateAndGetTargetSong()(using PlanContext): IO[Objects.MusicService.Song] = {
    logInfo(s"正在验证目标歌曲 ${songID} 是否存在") >> {
      GetSongByID(userID, userToken, songID).send.flatMap { case (songOpt, message) =>
        songOpt match {
          case Some(song) =>
            logInfo("目标歌曲存在性验证通过") >> IO.pure(song)
          case None =>
            IO.raiseError(new IllegalArgumentException(s"目标歌曲不存在: $message"))
        }
      }
    }
  }

  /**
   * 步骤4: 获取歌曲特征信息
   */
  private def getSongFeatures(song: Objects.MusicService.Song)(using PlanContext): IO[SongFeatures] = {
    for {
      _ <- logInfo("正在提取歌曲特征信息")
      
      // 获取歌曲的曲风信息
      genres <- getSongGenres(songID)
      _ <- logInfo(s"歌曲曲风: ${genres.mkString(", ")}")
      
      // 获取歌曲的创作者信息
      creators = song.creators ++ song.performers ++ song.composers
      _ <- logInfo(s"歌曲创作者: ${creators.mkString(", ")}")
      
      // 获取歌曲热度
      popularity <- StatisticsUtils.calculateSongPopularity(songID)
      _ <- logInfo(s"歌曲热度: ${popularity}")
      
      features = SongFeatures(
        songId = songID,
        genres = genres,
        creators = creators,
        popularity = popularity,
        releaseYear = song.releaseTime.getYear
      )
      
    } yield features
  }

  /**
   * 获取歌曲的曲风列表
   */
  private def getSongGenres(songId: String)(using PlanContext): IO[List[String]] = {
    val sql = s"SELECT genre_id FROM ${schemaName}.song_genre_mapping WHERE song_id = ?"
    readDBRows(sql, List(SqlParameter("String", songId))).map { rows =>
      rows.map(row => decodeField[String](row, "genre_id"))
    }
  }

  /**
   * 步骤5: 查找相似歌曲
   */
  private def findSimilarSongs(targetFeatures: SongFeatures)(using PlanContext): IO[List[String]] = {
    for {
      _ <- logInfo("开始查找相似歌曲")
      
      // 策略1: 相同曲风的歌曲
      genreSimilar <- findSongsBySameGenre(targetFeatures)
      
      // 策略2: 相同创作者的歌曲
      creatorSimilar <- findSongsBySameCreator(targetFeatures)
      
      // 策略3: 相似热度的歌曲
      popularitySimilar <- findSongsBySimilarPopularity(targetFeatures)
      
      // 策略4: 相似发布时间的歌曲
      timeSimilar <- findSongsBySimilarTime(targetFeatures)
      
      // 合并和排序相似歌曲
      allSimilar = combineAndRankSimilarSongs(
        targetFeatures,
        genreSimilar,
        creatorSimilar,
        popularitySimilar,
        timeSimilar
      )
      
      // 返回指定数量的相似歌曲
      result = allSimilar.take(limit)
      
    } yield result
  }

  /**
   * 策略1: 查找相同曲风的歌曲
   */
  private def findSongsBySameGenre(targetFeatures: SongFeatures)(using PlanContext): IO[List[SimilarSongCandidate]] = {
    if (targetFeatures.genres.isEmpty) {
      IO.pure(List.empty)
    } else {
      val genreParams = targetFeatures.genres.map(SqlParameter("String", _))
      val placeholders = targetFeatures.genres.map(_ => "?").mkString(",")
      
      val sql = s"""
        SELECT s.song_id, spc.popularity_score, COUNT(*) as genre_match_count
        FROM ${schemaName}.song_genre_mapping sgm
        JOIN ${schemaName}.song_table s ON sgm.song_id = s.song_id
        LEFT JOIN ${schemaName}.song_popularity_cache spc ON s.song_id = spc.song_id
        WHERE sgm.genre_id IN ($placeholders)
        AND s.song_id != ?
        GROUP BY s.song_id, spc.popularity_score
        ORDER BY genre_match_count DESC, COALESCE(spc.popularity_score, 0) DESC
        LIMIT ?
      """
      
      val params = genreParams ++ List(
        SqlParameter("String", targetFeatures.songId),
        SqlParameter("String", (limit * 2).toString)
      )
      
      readDBRows(sql, params).map { rows =>
        rows.map { row =>
          val songId = decodeField[String](row, "song_id")
          val popularity = Option(decodeField[Double](row, "popularity_score")).getOrElse(0.0)
          val genreMatchCount = decodeField[Int](row, "genre_match_count")
          val similarity = genreMatchCount.toDouble / targetFeatures.genres.length // 曲风匹配度
          SimilarSongCandidate(songId, similarity, "genre")
        }
      }
    }
  }

  /**
   * 策略2: 查找相同创作者的歌曲
   */
  private def findSongsBySameCreator(targetFeatures: SongFeatures)(using PlanContext): IO[List[SimilarSongCandidate]] = {
    if (targetFeatures.creators.isEmpty) {
      IO.pure(List.empty)
    } else {
      // 为每个创作者构建LIKE查询
      val creatorConditions = targetFeatures.creators.map { _ =>
        "(s.creators LIKE ? OR s.performers LIKE ? OR s.composers LIKE ?)"
      }.mkString(" OR ")
      
      val sql = s"""
        SELECT DISTINCT s.song_id, spc.popularity_score
        FROM ${schemaName}.song_table s
        LEFT JOIN ${schemaName}.song_popularity_cache spc ON s.song_id = spc.song_id
        WHERE ($creatorConditions)
        AND s.song_id != ?
        ORDER BY COALESCE(spc.popularity_score, 0) DESC
        LIMIT ?
      """
      
      val creatorParams = targetFeatures.creators.flatMap { creator =>
        val pattern = s"%${creator}%"
        List(
          SqlParameter("String", pattern),
          SqlParameter("String", pattern),
          SqlParameter("String", pattern)
        )
      }
      
      val params = creatorParams ++ List(
        SqlParameter("String", targetFeatures.songId),
        SqlParameter("String", (limit * 2).toString)
      )
      
      readDBRows(sql, params).map { rows =>
        rows.map { row =>
          val songId = decodeField[String](row, "song_id")
          SimilarSongCandidate(songId, 0.8, "creator") // 固定相似度
        }
      }
    }
  }

  /**
   * 策略3: 查找相似热度的歌曲
   */
  private def findSongsBySimilarPopularity(targetFeatures: SongFeatures)(using PlanContext): IO[List[SimilarSongCandidate]] = {
    val popularityRange = targetFeatures.popularity * 0.5 // ±50%的热度范围
    val minPopularity = targetFeatures.popularity - popularityRange
    val maxPopularity = targetFeatures.popularity + popularityRange
    
    val sql = s"""
      SELECT s.song_id, spc.popularity_score
      FROM ${schemaName}.song_table s
      JOIN ${schemaName}.song_popularity_cache spc ON s.song_id = spc.song_id
      WHERE spc.popularity_score BETWEEN ? AND ?
      AND s.song_id != ?
      ORDER BY ABS(spc.popularity_score - ?) ASC
      LIMIT ?
    """
    
    readDBRows(sql, List(
      SqlParameter("String", minPopularity.toString),
      SqlParameter("String", maxPopularity.toString),
      SqlParameter("String", targetFeatures.songId),
      SqlParameter("String", targetFeatures.popularity.toString),
      SqlParameter("String", (limit * 2).toString)
    )).map { rows =>
      rows.map { row =>
        val songId = decodeField[String](row, "song_id")
        val popularity = decodeField[Double](row, "popularity_score")
        val similarity = 1.0 - Math.abs(popularity - targetFeatures.popularity) / Math.max(popularity, targetFeatures.popularity)
        SimilarSongCandidate(songId, similarity, "popularity")
      }
    }
  }

  /**
   * 策略4: 查找相似发布时间的歌曲
   */
  private def findSongsBySimilarTime(targetFeatures: SongFeatures)(using PlanContext): IO[List[SimilarSongCandidate]] = {
    val yearRange = 3 // ±3年
    val minYear = targetFeatures.releaseYear - yearRange
    val maxYear = targetFeatures.releaseYear + yearRange
    
    val sql = s"""
      SELECT s.song_id, spc.popularity_score, 
             CAST(strftime('%Y', datetime(s.release_time/1000, 'unixepoch')) AS INTEGER) as release_year
      FROM ${schemaName}.song_table s
      LEFT JOIN ${schemaName}.song_popularity_cache spc ON s.song_id = spc.song_id
      WHERE CAST(strftime('%Y', datetime(s.release_time/1000, 'unixepoch')) AS INTEGER) BETWEEN ? AND ?
      AND s.song_id != ?
      ORDER BY COALESCE(spc.popularity_score, 0) DESC
      LIMIT ?
    """
    
    readDBRows(sql, List(
      SqlParameter("String", minYear.toString),
      SqlParameter("String", maxYear.toString),
      SqlParameter("String", targetFeatures.songId),
      SqlParameter("String", (limit * 2).toString)
    )).map { rows =>
      rows.map { row =>
        val songId = decodeField[String](row, "song_id")
        val releaseYear = decodeField[Int](row, "release_year")
        val yearDiff = Math.abs(releaseYear - targetFeatures.releaseYear)
        val similarity = 1.0 - (yearDiff.toDouble / yearRange)
        SimilarSongCandidate(songId, Math.max(0.1, similarity), "time")
      }
    }
  }

  /**
   * 合并和排序相似歌曲
   */
  private def combineAndRankSimilarSongs(
    targetFeatures: SongFeatures,
    genreSimilar: List[SimilarSongCandidate],
    creatorSimilar: List[SimilarSongCandidate],
    popularitySimilar: List[SimilarSongCandidate],
    timeSimilar: List[SimilarSongCandidate]
  ): List[String] = {
    // 合并所有候选歌曲，并计算综合相似度分数
    val allCandidates = (genreSimilar ++ creatorSimilar ++ popularitySimilar ++ timeSimilar)
      .groupBy(_.songId)
      .view.mapValues { candidates =>
        // 不同策略的权重
        val weights = Map(
          "genre" -> 0.4,
          "creator" -> 0.3,
          "popularity" -> 0.2,
          "time" -> 0.1
        )
        
        val totalScore = candidates.map { candidate =>
          candidate.similarity * weights.getOrElse(candidate.strategy, 0.1)
        }.sum
        
        totalScore
      }
      .toList
      .sortBy(-_._2) // 按相似度降序排序
      .map(_._1)
    
    allCandidates
  }

  private def logInfo(message: String): IO[Unit] = 
    IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))
    
  private def logError(message: String, cause: Throwable): IO[Unit] = 
    IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}

/**
 * 歌曲特征信息
 */
case class SongFeatures(
  songId: String,
  genres: List[String],
  creators: List[String],
  popularity: Double,
  releaseYear: Int
)

/**
 * 相似歌曲候选
 */
case class SimilarSongCandidate(
  songId: String,
  similarity: Double,
  strategy: String
)