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
 * Planner for GetNextSongRecommendation: 基于当前播放歌曲推荐下一首歌
 *
 * @param userID        用户ID
 * @param userToken     用户认证令牌
 * @param currentSongID 当前正在播放的歌曲ID
 * @param planContext   执行上下文
 */
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

      // 步骤1: 验证用户身份
      _ <- validateUser()

      // 步骤2: 验证当前歌曲存在性
      currentSong <- validateAndGetCurrentSong()

      // 步骤3: 获取用户画像
      userPortrait <- getUserPortrait()

      // 步骤4: 获取当前歌曲的曲风信息
      currentSongGenres <- getCurrentSongGenres()

      // 步骤5: 基于上下文推荐下一首歌
      nextSong <- recommendNextSong(currentSongGenres, userPortrait.vector)

    } yield nextSong

    logic.map { nextSongId =>
      (Some(nextSongId), "下一首歌推荐成功")
    }.handleErrorWith { error =>
      logError(s"为用户 ${userID} 推荐下一首歌失败", error) >>
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
   * 步骤2: 验证当前歌曲存在性并获取歌曲信息
   */
  private def validateAndGetCurrentSong()(using PlanContext): IO[Objects.MusicService.Song] = {
    logInfo(s"正在验证当前歌曲 ${currentSongID} 是否存在") >> {
      GetSongByID(userID, userToken, currentSongID).send.flatMap { case (songOpt, message) =>
        songOpt match {
          case Some(song) =>
            logInfo("当前歌曲存在性验证通过") >> IO.pure(song)
          case None =>
            IO.raiseError(new IllegalArgumentException(s"当前歌曲不存在: $message"))
        }
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
   * 步骤4: 获取当前歌曲的曲风信息
   */
  private def getCurrentSongGenres()(using PlanContext): IO[List[String]] = {
    val sql = s"SELECT genre_id FROM ${schemaName}.song_genre_mapping WHERE song_id = ?"
    readDBRows(sql, List(SqlParameter("String", currentSongID))).map { rows =>
      val genres = rows.map(row => decodeField[String](row, "genre_id"))
      logInfo(s"当前歌曲曲风: ${genres.mkString(", ")}")
      genres
    }
  }

  /**
   * 步骤5: 基于上下文推荐下一首歌
   */
  private def recommendNextSong(
    currentGenres: List[String],
    userPreferences: List[(String, Double)]
  )(using PlanContext): IO[String] = {
    for {
      // 获取用户最近播放的歌曲，避免重复
      recentSongs <- getRecentPlayedSongs()
      _ <- logInfo(s"用户最近播放歌曲 ${recentSongs.length} 首，将避免重复推荐")

      // 尝试不同的推荐策略
      recommendation <- tryRecommendationStrategies(currentGenres, userPreferences, recentSongs)
      
    } yield recommendation
  }

  /**
   * 获取用户最近播放的歌曲（最近50首）
   */
  private def getRecentPlayedSongs()(using PlanContext): IO[Set[String]] = {
    val sql = s"""
      SELECT song_id FROM ${schemaName}.playback_log 
      WHERE user_id = ? 
      ORDER BY play_time DESC 
      LIMIT 50
    """
    readDBRows(sql, List(SqlParameter("String", userID))).map { rows =>
      rows.map(row => decodeField[String](row, "song_id")).toSet
    }
  }

  /**
   * 尝试多种推荐策略
   */
  private def tryRecommendationStrategies(
    currentGenres: List[String],
    userPreferences: List[(String, Double)],
    excludeSongs: Set[String]
  )(using PlanContext): IO[String] = {
    for {
      // 策略1: 相同曲风 + 用户偏好
      strategy1Result <- recommendSameGenreWithPreference(currentGenres, userPreferences, excludeSongs)
      
      result <- strategy1Result match {
        case Some(songId) =>
          logInfo("策略1成功: 相同曲风 + 用户偏好") >> IO.pure(songId)
        case None =>
          // 策略2: 相同曲风 + 热度
          for {
            strategy2Result <- recommendSameGenreByPopularity(currentGenres, excludeSongs)
            result <- strategy2Result match {
              case Some(songId) =>
                logInfo("策略2成功: 相同曲风 + 热度") >> IO.pure(songId)
              case None =>
                // 策略3: 用户偏好曲风
                for {
                  strategy3Result <- recommendByUserPreference(userPreferences, excludeSongs)
                  result <- strategy3Result match {
                    case Some(songId) =>
                      logInfo("策略3成功: 用户偏好曲风") >> IO.pure(songId)
                    case None =>
                      // 策略4: 随机热门歌曲
                      logInfo("所有策略失败，使用随机热门歌曲") >>
                      getRandomPopularSong(excludeSongs)
                  }
                } yield result
            }
          } yield result
      }
    } yield result
  }

  /**
   * 策略1: 相同曲风 + 用户偏好
   */
  private def recommendSameGenreWithPreference(
    currentGenres: List[String],
    userPreferences: List[(String, Double)],
    excludeSongs: Set[String]
  )(using PlanContext): IO[Option[String]] = {
    if (currentGenres.isEmpty || userPreferences.isEmpty) {
      IO.pure(None)
    } else {
      // 计算当前歌曲曲风的用户偏好度
      val genrePreferences = currentGenres.flatMap { genre =>
        userPreferences.find(_._1 == genre).map(_._2)
      }
      
      if (genrePreferences.nonEmpty) {
        val avgPreference = genrePreferences.sum / genrePreferences.length
        // 如果用户对这些曲风偏好度较高，推荐相同曲风的歌曲
        if (avgPreference > 0.1) { // 阈值可调整
          getSameGenreSong(currentGenres, excludeSongs)
        } else {
          IO.pure(None)
        }
      } else {
        IO.pure(None)
      }
    }
  }

  /**
   * 策略2: 相同曲风 + 热度排序
   */
  private def recommendSameGenreByPopularity(
    currentGenres: List[String],
    excludeSongs: Set[String]
  )(using PlanContext): IO[Option[String]] = {
    if (currentGenres.isEmpty) {
      IO.pure(None)
    } else {
      getSameGenreSong(currentGenres, excludeSongs)
    }
  }

  /**
   * 获取相同曲风的歌曲
   */
  private def getSameGenreSong(
    genres: List[String],
    excludeSongs: Set[String]
  )(using PlanContext): IO[Option[String]] = {
    val genreParams = genres.map(SqlParameter("String", _))
    val placeholders = genres.map(_ => "?").mkString(",")
    
    val sql = s"""
      SELECT s.song_id, COALESCE(spc.popularity_score, 0) as popularity
      FROM ${schemaName}.song_genre_mapping sgm
      JOIN ${schemaName}.song_table s ON sgm.song_id = s.song_id
      LEFT JOIN ${schemaName}.song_popularity_cache spc ON s.song_id = spc.song_id
      WHERE sgm.genre_id IN ($placeholders)
      AND s.song_id != ?
      ORDER BY popularity DESC, s.song_id
      LIMIT 20
    """
    
    val params = genreParams :+ SqlParameter("String", currentSongID)
    readDBRows(sql, params).map { rows =>
      rows.map(row => decodeField[String](row, "song_id"))
        .filterNot(excludeSongs.contains)
        .headOption
    }
  }

  /**
   * 策略3: 基于用户偏好推荐
   */
  private def recommendByUserPreference(
    userPreferences: List[(String, Double)],
    excludeSongs: Set[String]
  )(using PlanContext): IO[Option[String]] = {
    if (userPreferences.isEmpty) {
      IO.pure(None)
    } else {
      // 选择用户偏好度最高的曲风
      val topGenre = userPreferences.maxBy(_._2)._1
      
      val sql = s"""
        SELECT s.song_id
        FROM ${schemaName}.song_genre_mapping sgm
        JOIN ${schemaName}.song_table s ON sgm.song_id = s.song_id
        LEFT JOIN ${schemaName}.song_popularity_cache spc ON s.song_id = spc.song_id
        WHERE sgm.genre_id = ?
        ORDER BY COALESCE(spc.popularity_score, 0) DESC, s.song_id
        LIMIT 10
      """
      
      readDBRows(sql, List(SqlParameter("String", topGenre))).map { rows =>
        rows.map(row => decodeField[String](row, "song_id"))
          .filterNot(excludeSongs.contains)
          .headOption
      }
    }
  }

  /**
   * 策略4: 随机热门歌曲
   */
  private def getRandomPopularSong(excludeSongs: Set[String])(using PlanContext): IO[String] = {
    val sql = s"""
      SELECT s.song_id
      FROM ${schemaName}.song_table s
      LEFT JOIN ${schemaName}.song_popularity_cache spc ON s.song_id = spc.song_id
      ORDER BY COALESCE(spc.popularity_score, 0) DESC, s.song_id
      LIMIT 50
    """
    
    readDBRows(sql, List()).flatMap { rows =>
      val candidates = rows.map(row => decodeField[String](row, "song_id"))
        .filterNot(excludeSongs.contains)
        .filterNot(_ == currentSongID)
      
      candidates.headOption match {
        case Some(songId) => IO.pure(songId)
        case None => 
          IO.raiseError(new RuntimeException("无法找到合适的推荐歌曲"))
      }
    }
  }

  private def logInfo(message: String): IO[Unit] = 
    IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))
    
  private def logError(message: String, cause: Throwable): IO[Unit] = 
    IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}