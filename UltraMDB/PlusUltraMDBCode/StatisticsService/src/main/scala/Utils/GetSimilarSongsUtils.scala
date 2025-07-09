// ===== src/main/scala/Utils/GetSimilarSongsUtils.scala =====

package Utils

import Common.API.PlanContext
import APIs.MusicService.{GetSongList, GetSongProfile}
import APIs.StatisticsService.GetSongPopularity
import Objects.StatisticsService.Profile
import cats.effect.IO
import cats.implicits._
import org.slf4j.LoggerFactory
import io.circe.generic.auto._

object GetSimilarSongsUtils {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

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

  private def fetchSongMetrics(userID: String, userToken: String, sID: String)(using PlanContext): IO[SongMetrics] =
    for {
      // 1. 串行执行两个 IO 操作
      profileResult <- GetSongProfile(userID, userToken, sID).send
      popularityResult <- GetSongPopularity(userID, userToken, sID).send

      // 2. 将两个结果组合成元组，然后对元组进行 match
      metrics <- (profileResult, popularityResult) match {
        // 匹配成功的情况
        case ((Some(profile), _), (Some(popularity), _)) =>
          IO.pure(SongMetrics(sID, profile, popularity))

        // 匹配失败的情况 (捕获所有其他组合)
        case ((profileOpt, profileMsg), (popOpt, popMsg)) =>
          val errorDetails = s"Profile: ${profileOpt.isDefined} ('$profileMsg'), Popularity: ${popOpt.isDefined} ('$popMsg')"
          IO.raiseError(new Exception(s"获取歌曲 ${sID} 的核心数据失败: $errorDetails"))
      }
    } yield metrics // 3. 将 match 的结果 (也是一个 IO) 返回

  private def fetchAllOtherSongs(userID: String, userToken: String, targetSongID: String)(using PlanContext): IO[List[String]] =
    GetSongList(userID, userToken).send.flatMap {
      case (Some(songIDs), _) => IO.pure(songIDs.filterNot(_ == targetSongID))
      case (None, msg) => IO.raiseError(new Exception(s"无法获取所有歌曲列表: $msg"))
    }

  private def fetchAllSongMetrics(userID: String, userToken: String, songIDs: List[String])(using planContext: PlanContext): IO[List[SongMetrics]] =
    songIDs.traverse { sID =>
      fetchSongMetrics(userID, userToken, sID).attempt.map {
        case Right(metrics) => Some(metrics)
        case Left(error) =>
          logger.warn(s"TID=${planContext.traceID.id} -- 获取候选歌曲 ${sID} 数据失败，将跳过此歌曲: ${error.getMessage}")
          None
      }
    }.map(_.flatten)

  private def rankSongs(target: SongMetrics, candidates: List[SongMetrics]): List[RankedSong] =
    candidates.map { candidate =>
      val matchScore = StatisticsUtils.calculateCosineSimilarity(target.profile, candidate.profile)
      val popularityFactor = Math.log1p(candidate.popularity)
      val finalScore = matchScore * popularityFactor
      RankedSong(candidate.songID, finalScore)
    }.filter(_.score > 0).sortBy(-_.score)

  private def logInfo(message: String)(using pc: PlanContext): IO[Unit] = IO(logger.info(s"TID=${pc.traceID.id} -- $message"))
}