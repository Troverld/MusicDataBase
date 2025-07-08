package Utils

import Common.API.PlanContext
import APIs.MusicService.GetSongProfile
import Objects.StatisticsService.{Dim, Profile}
import cats.effect.IO
import cats.implicits._
import org.slf4j.LoggerFactory
import io.circe.generic.auto._

object GetUserPortraitUtils {
  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  def generateUserProfile(userID: String, userToken: String)(using planContext: PlanContext): IO[Profile] = {
    // 权重系数，方便调整
    val ratingWeightCoefficient = 10.0

    for {
      _ <- logInfo("开始实时计算用户画像")

      // 1. 调用数据访问层获取历史记录
      histories <- (SearchUtils.fetchUserPlaybackHistory(userID), SearchUtils.fetchUserRatingHistory(userID)).parTupled
      (playedSongs, ratedSongsMap) = histories

      allInteractedSongIds = (playedSongs ++ ratedSongsMap.keys).toSet
      _ <- logInfo(s"用户总共交互过 ${allInteractedSongIds.size} 首歌曲")

      profile <- if (allInteractedSongIds.isEmpty) {
        logInfo("用户暂无交互记录，返回空画像") >>
          IO.pure(Profile(List.empty, norm = true))
      } else {
        // 2. 计算每首歌的交互分数
        val songInteractionScores = allInteractedSongIds.map { songId =>
          val ratingBonus = ratedSongsMap.get(songId).map(rating => (rating - 3.0) * ratingWeightCoefficient).getOrElse(0.0)
          val interactionScore = 1.0 + ratingBonus
          (songId, interactionScore)
        }.toMap

        for {
          // 3. 并行获取所有歌曲的曲风
          genresForSongsMap <- fetchGenresForSongs(allInteractedSongIds, userID, userToken)

          // 4. Map-Reduce 计算曲风偏好
          rawGenrePreferences = mapReduceGenreScores(songInteractionScores, genresForSongsMap)

          // 5. 过滤并格式化结果
          positivePreferences = rawGenrePreferences.toList.filter(_._2 > 0)
          preferenceDims = positivePreferences.map { case (genreId, score) => Dim(genreId, score) }

          // 6. 归一化
          rawProfile = Profile(vector = preferenceDims, norm = false)
          finalProfile = StatisticsUtils.normalizeVector(rawProfile)

          _ <- logInfo(s"计算出用户画像，包含 ${finalProfile.vector.length} 个曲风偏好")
        } yield finalProfile
      }
    } yield profile
  }

  private def mapReduceGenreScores(
    songScores: Map[String, Double],
    genreMap: Map[String, List[String]]
  ): Map[String, Double] = {
    val genreScorePairs = songScores.toList.flatMap {
      case (songId, score) =>
        val genres = genreMap.getOrElse(songId, List.empty)
        genres.map(genreId => (genreId, score))
    }

    genreScorePairs
      .groupBy { case (genreId, _) => genreId }
      .view
      .mapValues(_.map { case (_, score) => score }.sum)
      .toMap
  }

  private def fetchGenresForSongs(songIds: Set[String], userID: String, userToken: String)(using planContext: PlanContext): IO[Map[String, List[String]]] = {
    if (songIds.isEmpty) return IO.pure(Map.empty)

    logInfo(s"准备为 ${songIds.size} 首歌曲并行获取曲风Profile")

    songIds.toList.parTraverse { songId =>
      GetSongProfile(userID, userToken, songId).send.map {
        case (Some(profile), _) => songId -> profile.vector.map(_.GenreID)
        case (None, message) =>
          logger.warn(s"TID=${planContext.traceID.id} -- 获取歌曲 $songId 的Profile失败: $message. 该歌曲的曲风贡献将为空。")
          songId -> List.empty[String]
      }
    }.map(_.toMap)
  }

  private def logInfo(message: String)(using pc: PlanContext): IO[Unit] =
    IO(logger.info(s"TID=${pc.traceID.id} -- $message"))
}