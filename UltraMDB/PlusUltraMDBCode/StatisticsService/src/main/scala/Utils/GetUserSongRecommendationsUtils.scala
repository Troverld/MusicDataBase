package Utils

import Common.API.PlanContext
import APIs.StatisticsService.{GetUserPortrait, GetSongPopularity}
import APIs.MusicService.{GetSongList, GetSongProfile}
import Objects.StatisticsService.Profile
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory

object GetUserSongRecommendationsUtils {
  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  // -- 辅助数据结构 --
  private case class SongMetrics(songID: String, profile: Profile, popularity: Double)
  private case class RankedSong(songID: String, score: Double)

  def generateRecommendations(userID: String, userToken: String, pageNumber: Int, pageSize: Int)(using planContext: PlanContext): IO[List[String]] = {
    for {
      _ <- logInfo(s"在Utils层开始为用户 ${userID} 生成推荐，页码: ${pageNumber}，每页: ${pageSize}")

      userPortrait <- fetchUserPortrait(userID, userToken)
      allSongs <- fetchAllCandidateSongs(userID, userToken)

      rankedSongs <- if (userPortrait.vector.isEmpty) {
        logInfo("用户暂无画像数据，将推荐热门歌曲。") >> rankSongsByPopularity(allSongs, userID, userToken)
      } else {
        for {
          _ <- logInfo(s"用户画像获取成功。找到 ${allSongs.length} 首候选歌曲，开始计算个性化推荐...")
          candidateMetrics <- fetchAllSongMetrics(allSongs, userID, userToken)
          _ <- logInfo(s"成功获取了 ${candidateMetrics.length} 首候选歌曲的数据。")
          ranked = rankSongsBySimilarity(userPortrait, candidateMetrics)
        } yield ranked
      }

      paginatedResults = paginate(rankedSongs.map(_.songID), pageNumber, pageSize)
    } yield paginatedResults
  }

  private def fetchUserPortrait(userID: String, userToken: String)(using planContext: PlanContext): IO[Profile] =
    GetUserPortrait(userID, userToken).send.flatMap {
      case (Some(portrait), _) => IO.pure(portrait)
      case (None, msg) => logInfo(s"无法获取用户画像: $msg. 将使用空画像。") >> IO.pure(Profile(List.empty, norm = true))
    }

  private def fetchAllCandidateSongs(userID: String, userToken: String)(using planContext: PlanContext): IO[List[String]] =
    GetSongList(userID, userToken).send.flatMap {
      case (Some(songs), _) => IO.pure(songs)
      case (None, msg) => IO.raiseError(new Exception(s"无法获取所有歌曲列表: $msg"))
    }

  private def fetchAllSongMetrics(songIDs: List[String], userID: String, userToken: String)(using planContext: PlanContext): IO[List[SongMetrics]] =
    SearchUtils.fetchUserPlayedSongIds(userID).flatMap { playedSongs =>
      val songsToFetch = songIDs.filterNot(playedSongs.contains)
      logInfo(s"用户已听过 ${playedSongs.size} 首歌，将从 ${songsToFetch.length} 首未听过的歌曲中获取数据。")

      songsToFetch.traverse { songID =>
        fetchSongMetrics(songID, userID, userToken).attempt.map {
          case Right(metrics) => Some(metrics)
          case Left(error) =>
            logger.warn(s"TID=${planContext.traceID.id} -- 获取歌曲 ${songID} 数据失败，跳过: ${error.getMessage}")
            None
        }
      }.map(_.flatten)
    }

  private def fetchSongMetrics(songID: String, userID: String, userToken: String)(using planContext: PlanContext): IO[SongMetrics] =
    for {
      // 串行执行两个 IO 操作
      profileResult <- GetSongProfile(userID, userToken, songID).send
      popularityResult <- GetSongPopularity(userID, userToken, songID).send

      // 对两个操作的结果元组进行 match
      metrics <- (profileResult, popularityResult) match {
        // 成功情况：两个 API 都返回了 Some(...)
        case ((Some(profile), _), (Some(popularity), _)) =>
          IO.pure(SongMetrics(songID, profile, popularity))

        // 失败情况：任何一个或两个 API 调用失败
        case ((profileOpt, profileMsg), (popOpt, popMsg)) =>
          val errorDetails = s"Profile: ${profileOpt.isDefined} ('$profileMsg'), Popularity: ${popOpt.isDefined} ('$popMsg')"
          IO.raiseError(new Exception(s"获取歌曲 ${songID} 的核心数据失败: $errorDetails"))
      }
    } yield metrics

  private def rankSongsBySimilarity(userPortrait: Profile, candidates: List[SongMetrics]): List[RankedSong] = {
    candidates.map { song =>
      val matchScore = StatisticsUtils.calculateCosineSimilarity(userPortrait, song.profile)
      val popularityFactor = Math.log1p(song.popularity)
      val finalScore = matchScore * popularityFactor
      RankedSong(song.songID, finalScore)
    }.filter(_.score > 0).sortBy(-_.score)
  }

  private def rankSongsByPopularity(songIDs: List[String], userID: String, userToken: String)(using planContext: PlanContext): IO[List[RankedSong]] = {
    logInfo("正在并行获取所有歌曲的热度以进行热门推荐...")
    songIDs.traverse { songID =>
      GetSongPopularity(userID, userToken, songID).send
        .map(r => RankedSong(songID, r._1.getOrElse(0.0)))
        .attempt // Use attempt to prevent one failure from failing all
    }.map { results =>
      results.collect { case Right(rankedSong) if rankedSong.score > 0 => rankedSong }.sortBy(-_.score)
    }
  }

  private def paginate(recommendations: List[String], pageNumber: Int, pageSize: Int): List[String] = {
    val startIndex = (pageNumber - 1) * pageSize
    recommendations.slice(startIndex, startIndex + pageSize)
  }

  private def logInfo(message: String)(using pc: PlanContext): IO[Unit] = IO(logger.info(s"TID=${pc.traceID.id} -- $message"))
}