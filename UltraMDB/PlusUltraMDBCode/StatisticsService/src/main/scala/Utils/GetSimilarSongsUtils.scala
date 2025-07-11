// ===== src\main\scala\Utils\GetSimilarSongsUtils.scala ===== 

package Utils

import Common.API.PlanContext
import APIs.MusicService.{GetMultSongsProfiles, GetSongList, GetSongProfile} 
import Objects.StatisticsService.Profile
import cats.effect.IO
import cats.implicits._
import org.slf4j.LoggerFactory
import io.circe.generic.auto._

object GetSimilarSongsUtils {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)
  private val basiccoefficient = 10 // 基础系数，用于调整流行度对最终分数的影响

  private case class SongMetrics(songID: String, profile: Profile, popularity: Double)
  private case class RankedSong(songID: String, score: Double)

  def findSimilarSongs(
                        userID: String,
                        userToken: String,
                        songID: String,
                        limit: Int
                      )(using planContext: PlanContext): IO[List[String]] = {
    for {
      targetMetrics <- fetchSongMetrics(userID, userToken, songID)
      allOtherSongs <- fetchAllOtherSongs(userID, userToken, songID)
      _ <- logInfo(s"目标歌曲数据获取成功。找到 ${allOtherSongs.length} 首其他歌曲作为候选。")

      candidateMetrics <- fetchAllSongMetrics(userID, userToken, allOtherSongs)
      _ <- logInfo(s"成功获取了 ${candidateMetrics.length} 首候选歌曲的数据。")

      _ <- logInfo("正在计算综合推荐分并排序...")
      rankedSongs = rankSongs(targetMetrics, candidateMetrics)

      finalResult = rankedSongs.take(limit).map(_.songID)
      _ <- logInfo(s"查找完成，返回前 ${finalResult.length} 首最相似的歌曲。")
    } yield finalResult
  }

  private def fetchSongMetrics(userID: String, userToken: String, sID: String)(using PlanContext): IO[SongMetrics] = {
    // [REFACTORED] Removed parTupled for sequential execution.
    for {
      profileResult <- GetSongProfile(userID, userToken, sID).send
      popularity <- GetSongPopularityUtils.calculatePopularity(sID)

      profile <- profileResult match {
        case (Some(p), _) => IO.pure(p)
        case (None, msg) => IO.raiseError[Profile](new Exception(s"获取歌曲 $sID 的Profile失败: $msg"))
      }

    } yield SongMetrics(sID, profile, popularity)
  }.handleErrorWith { error =>
    IO.raiseError(new Exception(s"获取歌曲 $sID 的核心数据失败: ${error.getMessage}", error))
  }

  private def fetchAllOtherSongs(userID: String, userToken: String, targetSongID: String)(using PlanContext): IO[List[String]] =
  GetSongList(userID, userToken).send.flatMap {
    case (Some(songIDs), _) => IO.pure(songIDs.filterNot(_ == targetSongID))
    case (None, msg) => IO.raiseError(new Exception(s"无法获取所有歌曲列表: $msg"))
  }

  // ==================== 优化核心：重写 fetchAllSongMetrics ====================
  /**
   * 使用批量API高效获取所有候选歌曲的指标。
   * 此方法通过两个并行的批量请求替换了原来的N+1次请求。
   */
  private def fetchAllSongMetrics(userID: String, userToken: String, songIDs: List[String])(using planContext: PlanContext): IO[List[SongMetrics]] = {
    if (songIDs.isEmpty) {
      return IO.pure(List.empty)
    }
    
    logInfo(s"正在为 ${songIDs.length} 首歌曲批量获取 Profiles 和 Popularities...")

    // 1. 定义批量获取 Profile 的 IO 操作
    val profilesIO: IO[Map[String, Profile]] =
      GetMultSongsProfiles(userID, userToken, songIDs).send.flatMap {
        case (Some(profilesList), _) => IO.pure(profilesList.toMap)
        case (None, msg) => IO.raiseError(new Exception(s"批量获取歌曲 Profiles 失败: $msg"))
      }

    // 2. 定义批量计算 Popularity 的 IO 操作
    val popularitiesIO: IO[Map[String, Double]] =
      GetSongPopularityUtils.calculateBatchPopularity(songIDs)

    // 3. 并行执行这两个批量操作
    for {
      (profileMap, popularityMap) <- (profilesIO, popularitiesIO).parTupled

      // 4. 重组数据
      metrics = songIDs.flatMap { id =>
        profileMap.get(id) match {
          case Some(profile) =>
            // 如果Profile存在，则组合指标。流行度若不存在则默认为0。
            val popularity = popularityMap.getOrElse(id, 0.0)
            Some(SongMetrics(id, profile, popularity))
          case None =>
            // 如果歌曲的Profile在批量返回结果中缺失，记录警告并跳过该歌曲
            logger.warn(s"TID=${planContext.traceID.id} -- 在批量获取结果中未找到歌曲 ${id} 的Profile，将跳过。")
            None
        }
      }
    } yield metrics
  }
  // ==================== 优化结束 ====================

  private def rankSongs(target: SongMetrics, candidates: List[SongMetrics]): List[RankedSong] =
    candidates.map { candidate =>
      val matchScore = StatisticsUtils.calculateCosineSimilarity(target.profile, candidate.profile)
      val popularityFactor = Math.log1p(candidate.popularity+basiccoefficient)
      val finalScore = matchScore * popularityFactor
      RankedSong(candidate.songID, finalScore)
    }.filter(_.score > 0).sortBy(-_.score)

  private def logInfo(message: String)(using pc: PlanContext): IO[Unit] = IO(logger.info(s"TID=${pc.traceID.id} -- $message"))
}
// ===== End of src\main\scala\Utils\GetSimilarSongsUtils.scala ===== 