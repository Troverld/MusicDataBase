// ===== src\main\scala\Utils\GetUserSongRecommendationsUtils.scala ===== 

package Utils

import Common.API.PlanContext
import APIs.MusicService.{GetSongList, GetSongProfile}
import Objects.StatisticsService.Profile
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory

object GetUserSongRecommendationsUtils {
  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

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

  private def fetchUserPortrait(userID: String, userToken: String)(using planContext: PlanContext): IO[Profile] = {
    GetUserPortraitUtils.generateUserProfile(userID, userToken)
      .handleErrorWith { error =>
        logInfo(s"无法获取用户画像: ${error.getMessage}. 将使用空画像。") >>
          IO.pure(Profile(List.empty, norm = true))
      }
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

  private def fetchSongMetrics(songID: String, userID: String, userToken: String)(using planContext: PlanContext): IO[SongMetrics] = {
    // [REFACTORED] Removed parTupled for sequential execution.
    for {
      profileResult <- GetSongProfile(userID, userToken, songID).send
      popularity <- GetSongPopularityUtils.calculatePopularity(songID)

      profile <- profileResult match {
        case (Some(p), _) => IO.pure(p)
        case (None, msg) => IO.raiseError[Profile](new Exception(s"获取歌曲 $songID Profile失败: $msg"))
      }

    } yield SongMetrics(songID, profile, popularity)
  }.handleErrorWith { error =>
    IO.raiseError(new Exception(s"获取歌曲 ${songID} 的核心数据失败: ${error.getMessage}", error))
  }

  private def rankSongsBySimilarity(userPortrait: Profile, candidates: List[SongMetrics]): List[RankedSong] = {
    candidates.map { song =>
      val matchScore = StatisticsUtils.calculateCosineSimilarity(userPortrait, song.profile)
      val popularityFactor = Math.log1p(song.popularity)
      val finalScore = matchScore * popularityFactor
      RankedSong(song.songID, finalScore)
    }.filter(_.score > 0).sortBy(-_.score)
  }

  private def rankSongsByPopularity(songIDs: List[String], userID: String, userToken: String)(using planContext: PlanContext): IO[List[RankedSong]] = {
    logInfo(s"正在批量获取 ${songIDs.length} 首歌曲的热度...")
    GetSongPopularityUtils.calculateBatchPopularity(songIDs).map { popularityMap =>
      songIDs.map(id => RankedSong(id, popularityMap.getOrElse(id, 0.0)))
        .filter(_.score > 0)
        .sortBy(-_.score)
    }
  }

  private def paginate(recommendations: List[String], pageNumber: Int, pageSize: Int): List[String] = {
    val startIndex = (pageNumber - 1) * pageSize
    recommendations.slice(startIndex, startIndex + pageSize)
  }

  private def logInfo(message: String)(using pc: PlanContext): IO[Unit] = IO(logger.info(s"TID=${pc.traceID.id} -- $message"))
}
// ===== End of src\main\scala\Utils\GetUserSongRecommendationsUtils.scala ===== 