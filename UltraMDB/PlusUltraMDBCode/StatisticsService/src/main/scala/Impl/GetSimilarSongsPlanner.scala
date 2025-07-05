package Impl

import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import APIs.OrganizeService.validateUserMapping
import APIs.MusicService.{GetSongByID, GetSongList, GetSongProfile}
import APIs.StatisticsService.GetSongPopularity
import Objects.StatisticsService.{Profile, Dim}
import Utils.StatisticsUtils
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory

/**
 * Planner for GetSimilarSongs: 获取与指定歌曲相似的歌曲列表 (最终重构版)
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

  // -- 辅助数据结构 --
  case class SongMetrics(songID: String, profile: Profile, popularity: Double)
  case class RankedSong(songID: String, score: Double)

  override def plan(using planContext: PlanContext): IO[(Option[List[String]], String)] = {
    val logic: IO[List[String]] = for {
      _ <- logInfo(s"开始查找与歌曲 ${songID} 相似的歌曲，限制数量: ${limit}")
      _ <- validateUser()
      _ <- validateParams()
      _ <- validateTargetSong() // 验证目标歌曲存在性

      initialData <- (
        fetchSongMetrics(songID),
        fetchAllOtherSongs()
      ).parTupled
      (targetMetrics, allOtherSongs) = initialData
      _ <- logInfo(s"目标歌曲数据获取成功。找到 ${allOtherSongs.length} 首其他歌曲作为候选。")

      candidateMetrics <- fetchAllSongMetrics(allOtherSongs)
      _ <- logInfo(s"成功获取了 ${candidateMetrics.length} 首候选歌曲的数据。")

      _ <- logInfo("正在计算综合推荐分并排序...")
      rankedSongs = rankSongs(targetMetrics, candidateMetrics)

      finalResult = rankedSongs.take(limit).map(_.songID)
      _ <- logInfo(s"查找完成，返回前 ${finalResult.length} 首最相似的歌曲。")

    } yield finalResult

    logic.map { similarSongs =>
      (Some(similarSongs), "相似歌曲查找成功")
    }.handleErrorWith { error =>
      logError(s"查找歌曲 ${songID} 的相似歌曲失败", error) >>
        IO.pure((None, error.getMessage))
    }
  }

  // --- 验证逻辑 ---
  private def validateUser()(using PlanContext): IO[Unit] = {
    logInfo("正在验证用户身份") >> validateUserMapping(userID, userToken).send.flatMap {
      case (true, _) => logInfo("用户身份验证通过")
      case (false, msg) => IO.raiseError(new IllegalArgumentException(s"用户身份验证失败: $msg"))
    }
  }

  private def validateParams()(using PlanContext): IO[Unit] = {
    logInfo(s"验证参数: limit=${limit}") >> {
      if (limit <= 0 || limit > 100) IO.raiseError(new IllegalArgumentException("相似歌曲数量限制必须在1-100之间"))
      else IO.unit
    }
  }

  private def validateTargetSong()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证目标歌曲 ${songID} 是否存在") >>
      GetSongByID(userID, userToken, songID).send.flatMap {
        case (Some(_), _) => logInfo("目标歌曲存在性验证通过")
        case (None, message) => IO.raiseError(new IllegalArgumentException(s"目标歌曲不存在: $message"))
      }
  }

  // --- 数据获取逻辑 ---

  private def fetchSongMetrics(sID: String)(using PlanContext): IO[SongMetrics] = {
    (
      GetSongProfile(userID, userToken, sID).send,
      GetSongPopularity(userID, userToken, sID).send
    ).parTupled.flatMap {
      case ((Some(profile), _), (Some(popularity), _)) =>
        IO.pure(SongMetrics(sID, profile, popularity))
      case ((profileOpt, profileMsg), (popOpt, popMsg)) =>
        val errorDetails = s"Profile: ${profileOpt.isDefined} ($profileMsg), Popularity: ${popOpt.isDefined} ($popMsg)"
        IO.raiseError(new Exception(s"获取歌曲 ${sID} 的核心数据失败: $errorDetails"))
    }
  }

  private def fetchAllOtherSongs()(using PlanContext): IO[List[String]] = {
    GetSongList(userID, userToken).send.flatMap {
      // **核心修正点**: GetSongList 返回的是 List[String]，直接使用即可
      case (Some(songIDs), _) => IO.pure(songIDs.filterNot(_ == songID))
      case (None, msg)         => IO.raiseError(new Exception(s"无法获取所有歌曲列表: $msg"))
    }
  }

  private def fetchAllSongMetrics(songIDs: List[String])(using PlanContext): IO[List[SongMetrics]] = {
    songIDs.parTraverse { sID =>
      fetchSongMetrics(sID).attempt.map {
        case Right(metrics) => Some(metrics)
        case Left(error) =>
          logger.warn(s"TID=${planContext.traceID.id} -- 获取候选歌曲 ${sID} 数据失败，将跳过此歌曲: ${error.getMessage}")
          None
      }
    }.map(_.flatten)
  }

  // --- 核心排序逻辑 ---

  private def rankSongs(target: SongMetrics, candidates: List[SongMetrics]): List[RankedSong] = {
    candidates.map { candidate =>
        val matchScore = StatisticsUtils.calculateCosineSimilarity(target.profile, candidate.profile)
        val popularityFactor = Math.log1p(candidate.popularity)
        val finalScore = matchScore * popularityFactor
        RankedSong(candidate.songID, finalScore)
      }
      .filter(_.score > 0)
      .sortBy(-_.score)
  }

  private def logInfo(message: String): IO[Unit] =
    IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))

  private def logError(message: String, cause: Throwable): IO[Unit] =
    IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}