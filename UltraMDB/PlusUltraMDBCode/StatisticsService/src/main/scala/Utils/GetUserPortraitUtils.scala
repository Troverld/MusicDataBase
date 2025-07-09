package Utils

import Common.API.PlanContext
import APIs.MusicService.{GetMultiSongsProfiles, GetSongProfile}
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
      playedSongs <- SearchUtils.fetchUserPlaybackHistory(userID)
      ratedSongsMap <- SearchUtils.fetchUserRatingHistory(userID)

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
          // 3. 并行获取所有歌曲的Profile
          songProfilesMap <- fetchGenresForSongs(allInteractedSongIds, userID, userToken)

          // 4. 计算加权Profile总和
          rawProfile = mapReduceGenreScores(songInteractionScores, songProfilesMap)

          // 5. 归一化
          finalProfile = StatisticsUtils.normalizeVector(rawProfile)

          _ <- logInfo(s"计算出用户画像，包含 ${finalProfile.vector.length} 个曲风偏好")
        } yield finalProfile
      }
    } yield profile
  }

  private def mapReduceGenreScores(
    songScores: Map[String, Double],
    genreMap: Map[String, Profile]  // Changed from Map[String, List[String]]
  ): Profile = {
    songScores.foldLeft(Profile(List.empty, norm = false)) {
      case (accumulatedProfile, (songId, score)) =>
        genreMap.get(songId) match {
          case Some(songProfile) =>
            val weightedProfile = StatisticsUtils.multiply(songProfile, score)
            StatisticsUtils.add(accumulatedProfile, weightedProfile)
          case None =>
            accumulatedProfile
        }
    }
  }

  private def fetchGenresForSongs(songIds: Set[String], userID: String, userToken: String)(using planContext: PlanContext): IO[Map[String, Profile]] = {
    if (songIds.isEmpty) return IO.pure(Map.empty)

    logInfo(s"准备批量获取 ${songIds.size} 首歌曲的曲风Profile")

    val songIdsList = songIds.toList

    GetMultSongsProfiles(userID, userToken, songIdsList).send.flatMap {
      case (Some(profiles), _) =>
        IO.pure(songIdsList.zip(profiles).toMap)
      
      case (None, message) =>
        logInfo(s"批量获取歌曲Profile失败: $message. 将回退到单曲获取方式") >>
          songIds.toList.traverse { songId =>
            GetSongProfile(userID, userToken, songId).send.map {
              case (Some(profile), _) => songId -> profile
              case (None, message) =>
                logger.warn(s"TID=${planContext.traceID.id} -- 获取歌曲 $songId 的Profile失败: $message. 该歌曲的曲风贡献将为空。")
                songId -> Profile(List.empty, norm = true)
            }
          }.map(_.toMap)
    }
  }
  private def logInfo(message: String)(using pc: PlanContext): IO[Unit] =
    IO(logger.info(s"TID=${pc.traceID.id} -- $message"))
}