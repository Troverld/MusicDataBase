package Impl

import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import APIs.OrganizeService.validateUserMapping
import APIs.StatisticsService.{GetUserPortrait, GetSongPopularity}
import APIs.MusicService.{GetSongList,GetSongProfile}
import Objects.StatisticsService.{Profile, Dim}
import Utils.StatisticsUtils
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory

/**
 * Planner for GetUserSongRecommendations: 根据用户画像推荐歌曲 (重构版)
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

  // -- 辅助数据结构 --
  case class SongMetrics(songID: String, profile: Profile, popularity: Double)
  case class RankedSong(songID: String, score: Double)

  override def plan(using planContext: PlanContext): IO[(Option[List[String]], String)] = {
    val logic: IO[List[String]] = for {
      _ <- logInfo(s"开始为用户 ${userID} 推荐歌曲，页码: ${pageNumber}，每页: ${pageSize}")
      _ <- validateUser()
      _ <- validatePaginationParams()

      initialData <- (
        fetchUserPortrait(),
        fetchAllCandidateSongs()
      ).parTupled
      (userPortrait, allSongs) = initialData

      // **核心修正点**: 将 if/else 的两个分支都构造成返回 IO[List[RankedSong]] 的形式
      recommendations <- if (userPortrait.vector.isEmpty) {
        logInfo("用户暂无画像数据，将推荐热门歌曲。") >> rankSongsByPopularity(allSongs)
      } else {
        // **修正点**: 将整个个性化推荐逻辑包裹在一个 for-comprehension 中
        for {
          _ <- logInfo(s"用户画像获取成功。找到 ${allSongs.length} 首候选歌曲，开始计算个性化推荐...")
          _ <- logInfo("正在并行获取所有候选歌曲的统计数据 (此操作可能耗时较长)...")
          candidateMetrics <- fetchAllSongMetrics(allSongs)
          _ <- logInfo(s"成功获取了 ${candidateMetrics.length} 首候选歌曲的数据。")
          _ <- logInfo("正在计算综合推荐分并排序...")
          // 纯计算，使用 val 绑定
          rankedSongs = rankSongs(userPortrait, candidateMetrics)
        } yield rankedSongs // 使用 yield 产出最终结果
      }

      paginatedResults = paginate(recommendations.map(_.songID))
      _ <- logInfo(s"推荐完成，返回 ${paginatedResults.length} 首歌曲。")

    } yield paginatedResults

    logic.map { recommendations =>
      (Some(recommendations), "歌曲推荐成功")
    }.handleErrorWith { error =>
      logError(s"为用户 ${userID} 推荐歌曲失败", error) >>
        IO.pure((None, error.getMessage))
    }
  }

  private def validateUser()(using PlanContext): IO[Unit] = {
    logInfo("正在验证用户身份") >> validateUserMapping(userID, userToken).send.flatMap {
      case (true, _) => logInfo("用户身份验证通过")
      case (false, msg) => IO.raiseError(new IllegalArgumentException(s"用户身份验证失败: $msg"))
    }
  }

  private def validatePaginationParams()(using PlanContext): IO[Unit] = {
    logInfo(s"验证分页参数: pageNumber=${pageNumber}, pageSize=${pageSize}") >> {
      if (pageNumber <= 0) IO.raiseError(new IllegalArgumentException("页码必须大于0"))
      else if (pageSize <= 0 || pageSize > 100) IO.raiseError(new IllegalArgumentException("每页数量必须在1-100之间"))
      else IO.unit
    }
  }

  private def fetchUserPortrait()(using PlanContext): IO[Profile] = {
    GetUserPortrait(userID, userToken).send.flatMap {
      case (Some(portrait), _) => IO.pure(portrait)
      case (None, msg) =>
        logInfo(s"无法获取用户画像: $msg. 将使用空画像。") >> IO.pure(Profile(List.empty, norm = true))
    }
  }

  private def fetchAllCandidateSongs()(using PlanContext): IO[List[String]] = {
    GetSongList(userID, userToken).send.flatMap {
      case (Some(songs), _) => IO.pure(songs)
      case (None, msg)      => IO.raiseError(new Exception(s"无法获取所有歌曲列表: $msg"))
    }
  }

  private def fetchSongMetrics(songID: String)(using PlanContext): IO[SongMetrics] = {
    (
      GetSongProfile(userID, userToken, songID).send,
      GetSongPopularity(userID, userToken, songID).send
    ).parTupled.flatMap {
      case ((Some(profile), _), (Some(popularity), _)) =>
        IO.pure(SongMetrics(songID, profile, popularity))
      case ((profileOpt, profileMsg), (popOpt, popMsg)) =>
        val errorDetails = s"Profile: ${profileOpt.isDefined} ($profileMsg), Popularity: ${popOpt.isDefined} ($popMsg)"
        IO.raiseError(new Exception(s"获取歌曲 ${songID} 的核心数据失败: $errorDetails"))
    }
  }

  private def fetchAllSongMetrics(songIDs: List[String])(using PlanContext): IO[List[SongMetrics]] = {
    val playedSongsIO = readDBRows(s"SELECT DISTINCT song_id FROM ${schemaName}.playback_log WHERE user_id = ?", List(SqlParameter("String", userID)))
      .map(_.flatMap(row => row.hcursor.get[String]("song_id").toOption).toSet)

    playedSongsIO.flatMap { playedSongs =>
      val songsToFetch = songIDs.filterNot(playedSongs.contains)
      logInfo(s"用户已听过 ${playedSongs.size} 首歌，将从 ${songsToFetch.length} 首未听过的歌曲中获取数据。")

      songsToFetch.parTraverse { songID =>
        fetchSongMetrics(songID).attempt.map {
          case Right(metrics) => Some(metrics)
          case Left(error) =>
            logger.warn(s"TID=${planContext.traceID.id} -- 获取歌曲 ${songID} 数据失败，将跳过此歌曲: ${error.getMessage}")
            None
        }
      }.map(_.flatten)
    }
  }

  private def rankSongs(userPortrait: Profile, candidates: List[SongMetrics]): List[RankedSong] = {
    candidates.map { song =>
        val matchScore = StatisticsUtils.calculateCosineSimilarity(userPortrait, song.profile)
        val popularityFactor = Math.log1p(song.popularity)
        val finalScore = matchScore * popularityFactor
        RankedSong(song.songID, finalScore)
      }
      .filter(_.score > 0)
      .sortBy(-_.score)
  }

  private def rankSongsByPopularity(songIDs: List[String])(using PlanContext): IO[List[RankedSong]] = {
    logInfo("正在并行获取所有歌曲的热度以进行热门推荐...")
    songIDs.parTraverse { songID =>
      GetSongPopularity(userID, userToken, songID).send
        .map(r => RankedSong(songID, r._1.getOrElse(0.0)))
        .attempt
    }.map { results =>
      results.collect { case Right(rankedSong) if rankedSong.score > 0 => rankedSong }
        .sortBy(-_.score)
    }
  }

  private def paginate(recommendations: List[String]): List[String] = {
    val startIndex = (pageNumber - 1) * pageSize
    recommendations.slice(startIndex, startIndex + pageSize)
  }

  private def logInfo(message: String): IO[Unit] =
    IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))

  private def logError(message: String, cause: Throwable): IO[Unit] =
    IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}