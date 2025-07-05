package Impl

import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import APIs.OrganizeService.validateUserMapping
import APIs.MusicService.{GetSongByID, GetSongProfile, FilterSongsByEntity}
import APIs.StatisticsService.{GetUserPortrait, GetSongPopularity, GetUserSongRecommendations}
import Objects.StatisticsService.{Dim, Profile}
import Utils.StatisticsUtils
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory

case class GetNextSongRecommendationPlanner(
                                             userID: String,
                                             userToken: String,
                                             currentSongID: String,
                                             override val planContext: PlanContext
                                           ) extends Planner[(Option[String], String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  // 超参数常量
  private val RECENT_SONGS_LIMIT = 10
  private val CANDIDATE_SONGS_LIMIT = 5
  private val TOP_N_SONGS_FOR_SAMPLING = 5
  private val FALLBACK_PAGE_SIZE = 20
  private val PREFERENCE_THRESHOLD = 0.05
  private val SOFTMAX_PREFERENCE_THRESHOLD = 0.2

  private type RecommendationStrategy = Set[String] => IO[Option[String]]

  override def plan(using planContext: PlanContext): IO[(Option[String], String)] = {
    val logic: IO[String] = for {
      _ <- logInfo(s"开始为用户 ${userID} 基于当前歌曲 ${currentSongID} 推荐下一首歌")
      _ <- validateUser()
      _ <- validateCurrentSong()

      recommendationData <- (getUserPortrait(), getSongGenres(currentSongID), getRecentPlayedSongs()).parTupled
      (userPortrait, currentSongGenres, recentPlayedSongs) = recommendationData
      _ <- logInfo(s"获取到用户画像，当前歌曲曲风: [${currentSongGenres.mkString(", ")}], 最近播放: ${recentPlayedSongs.size}首")

      strategies = List(
        recommendSameGenre(currentSongGenres, userPortrait),
        recommendByUserTopGenre(userPortrait),
        fallbackRecommendation()
      )

      nextSongId <- tryStrategies(strategies, recentPlayedSongs + currentSongID)
    } yield nextSongId

    logic.map { songId =>
      (Some(songId), "下一首歌推荐成功")
    }.handleErrorWith { error =>
      logError(s"为用户 ${userID} 推荐下一首歌失败", error) >> IO.pure((None, error.getMessage))
    }
  }

  private def validateUser()(using PlanContext): IO[Unit] = {
    logInfo("正在验证用户身份") >>
      validateUserMapping(userID, userToken).send.flatMap {
        case (true, _) => logInfo("用户身份验证通过")
        case (false, message) => IO.raiseError(new IllegalArgumentException(s"用户身份验证失败: $message"))
      }
  }

  private def validateCurrentSong()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证当前歌曲 ${currentSongID} 是否存在") >>
      GetSongByID(userID, userToken, currentSongID).send.flatMap {
        case (Some(_), _) => logInfo("当前歌曲存在性验证通过")
        case (None, message) => IO.raiseError(new IllegalStateException(s"当前歌曲不存在: $message"))
      }
  }

  private def getUserPortrait()(using PlanContext): IO[Profile] = {
    logInfo("正在通过API获取用户画像") >>
      GetUserPortrait(userID, userToken).send.flatMap {
        case (Some(portrait), _) => IO.pure(portrait)
        case (None, msg) =>
          logInfo(s"无法获取用户画像: $msg. 将使用空画像。") >>
            IO.pure(Profile(List.empty, norm = true))
      }
  }

  private def getSongGenres(songId: String)(using PlanContext): IO[List[String]] = {
    logInfo(s"正在通过API获取歌曲 ${songId} 的曲风") >>
      GetSongProfile(userID, userToken, songId).send.map {
        case (Some(profile), _) => profile.vector.map(_.GenreID)
        case (None, msg) =>
          logger.warn(s"TID=${planContext.traceID.id} -- 获取歌曲 $songId 的Profile失败: $msg. 将视为空曲风列表。")
          List.empty[String]
      }
  }

  private def getRecentPlayedSongs()(using PlanContext): IO[Set[String]] = {
    logInfo(s"正在查询用户最近播放的 ${RECENT_SONGS_LIMIT} 首歌曲")
    val sql = s"SELECT song_id FROM ${schemaName}.playback_log WHERE user_id = ? ORDER BY play_time DESC LIMIT ?"
    readDBRows(sql, List(SqlParameter("String", userID), SqlParameter("Int", RECENT_SONGS_LIMIT.toString)))
      .map(_.map(decodeField[String](_, "song_id")).toSet)
  }

  private def tryStrategies(strategies: List[RecommendationStrategy], excludeSongs: Set[String])(using PlanContext): IO[String] = {
    strategies.foldLeft(IO.pure(None: Option[String])) { (acc, strategy) =>
      acc.flatMap {
        case Some(songId) => IO.pure(Some(songId))
        case None         => strategy(excludeSongs)
      }
    }.flatMap {
      case Some(songId) => IO.pure(songId)
      case None => IO.raiseError(new RuntimeException("所有推荐策略均失败，无法找到合适的歌曲"))
    }
  }

  private def recommendSameGenre(currentGenres: List[String], userPortrait: Profile)(using PlanContext): RecommendationStrategy = excludeSongs => {
    val genresWithPreference = currentGenres.flatMap(g => userPortrait.vector.find(_.GenreID == g))
    if (genresWithPreference.isEmpty) {
      logInfo("策略1: 用户对当前曲风无偏好记录，跳过") >> IO.pure(None)
    } else {
      val avgPreference = genresWithPreference.map(_.value).sum / genresWithPreference.length
      logInfo(s"策略1: 用户对当前曲风的平均偏好度为: $avgPreference")

      if (avgPreference >= PREFERENCE_THRESHOLD) {
        logInfo(s"偏好度高于阈值 $PREFERENCE_THRESHOLD, 准备从当前曲风中采样")
        val genreProfileToSample = Profile(vector = genresWithPreference, norm = false)
        StatisticsUtils.softmaxSample(genreProfileToSample) match {
          case Some(sampledGenre) =>
            logInfo(s"从当前歌曲曲风中采样选中: '$sampledGenre'")
            findSongInGenreByPopularity(sampledGenre, excludeSongs)
          case None => IO.pure(None)
        }
      } else {
        logInfo(s"偏好度低于阈值 $PREFERENCE_THRESHOLD, 跳过") >> IO.pure(None)
      }
    }
  }

  private def recommendByUserTopGenre(userPortrait: Profile)(using PlanContext): RecommendationStrategy = excludeSongs => {
    val highlyLikedGenres = userPortrait.vector.filter(_.value > SOFTMAX_PREFERENCE_THRESHOLD)
    if (highlyLikedGenres.isEmpty) {
      logInfo(s"策略2: 用户没有偏好度高于 ${SOFTMAX_PREFERENCE_THRESHOLD} 的曲风，跳过") >> IO.pure(None)
    } else {
      val likedGenresProfile = Profile(vector = highlyLikedGenres, norm = false)
      StatisticsUtils.softmaxSample(likedGenresProfile) match {
        case Some(sampledGenre) =>
          logInfo(s"策略2: 从 ${highlyLikedGenres.length} 个高偏好曲风中采样选中: '$sampledGenre'")
          findSongInGenreByPopularity(sampledGenre, excludeSongs)
        case None => IO.pure(None)
      }
    }
  }

  private def fallbackRecommendation()(using PlanContext): RecommendationStrategy = excludeSongs => {
    logInfo("策略3: 启动后备推荐策略")
    GetUserSongRecommendations(userID, userToken, pageSize = FALLBACK_PAGE_SIZE).send.flatMap {
      case (Some(recommendedIds), _) =>
        IO.pure(recommendedIds.find(id => !excludeSongs.contains(id)))
      case (None, msg) =>
        logInfo(s"后备推荐API调用失败: $msg").as(None)
    }
  }

  private def findSongInGenreByPopularity(genre: String, excludeSongs: Set[String])(using PlanContext): IO[Option[String]] = {
    for {
      candidateIds <- FilterSongsByEntity(userID, userToken, genres = Some(genre)).send.map(_._1.getOrElse(List.empty))

      candidates = candidateIds
        .filterNot(excludeSongs.contains)
        .take(CANDIDATE_SONGS_LIMIT)

      songsWithPopularity <- candidates.parTraverse { songId =>
        GetSongPopularity(userID, userToken, songId).send.map(r => (songId, r._1.getOrElse(0.0)))
      }

      topSongs = songsWithPopularity.sortBy(-_._2).take(TOP_N_SONGS_FOR_SAMPLING)

      sampledSong <- if (topSongs.isEmpty) {
        IO.pure(None)
      } else {
        val songDims = topSongs.map { case (id, popularity) => Dim(id, popularity) }
        val songsProfile = Profile(vector = songDims, norm = false)
        IO.pure(StatisticsUtils.softmaxSample(songsProfile))
      }

    } yield sampledSong
  }

  private def logInfo(message: String): IO[Unit] =
    IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))

  private def logError(message: String, cause: Throwable): IO[Unit] =
    IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}