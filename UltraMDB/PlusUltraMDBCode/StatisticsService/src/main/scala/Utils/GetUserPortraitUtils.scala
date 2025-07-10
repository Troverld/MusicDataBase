// ===== src/main/scala/Utils/GetUserPortraitUtils.scala (MODIFIED) =====

package Utils

import Common.API.PlanContext
import APIs.MusicService.{GetMultSongsProfiles, GetSongProfile}
import Objects.StatisticsService.{Dim, Profile}
import cats.effect.IO
import cats.implicits._
import org.slf4j.LoggerFactory
import io.circe.generic.auto._

object GetUserPortraitUtils {
  private val logger = DebugLoggerFactory.getLogger(getClass)

  // [REFACTORED] 新的评分函数
  private def ratingToBonus(rating: Int): Double = {
    (rating * rating)
  }

  def generateUserProfile(userID: String, userToken: String)(using planContext: PlanContext): IO[Profile] = {
    for {
      _ <- logInfo("开始实时计算用户画像")

      // 1. 调用数据访问层获取历史记录
      playedSongsList <- SearchUtils.fetchUserPlaybackHistory(userID) // 这是一个包含重复ID的列表
      ratedSongsMap <- SearchUtils.fetchUserRatingHistory(userID)

      // [REFACTORED] 从播放历史列表中计算每首歌的播放次数
      playedSongsCountMap = playedSongsList.groupBy(identity).view.mapValues(_.size).toMap

      // 合并所有交互过的歌曲ID
      allInteractedSongIds = (playedSongsCountMap.keys ++ ratedSongsMap.keys).toSet
      _ <- logInfo(s"用户总共交互过 ${allInteractedSongIds.size} 首不重复的歌曲")

      profile <- if (allInteractedSongIds.isEmpty) {
        logInfo("用户暂无交互记录，返回空画像") >>
          IO.pure(Profile(List.empty, norm = true))
      } else {
        // 2. 计算每首歌的交互分数
        val songInteractionScores = allInteractedSongIds.map { songId =>
          // 播放次数作为基础分
          val playCount = playedSongsCountMap.getOrElse(songId, 0).toDouble
          // [REFACTORED] 使用新的评分函数计算评分奖励
          val ratingBonus = ratedSongsMap.get(songId).map(ratingToBonus).getOrElse(0.0)

          val interactionScore = playCount + ratingBonus
          (songId, interactionScore)
        }.toMap

        for {
          songProfilesMap <- fetchGenresForSongs(allInteractedSongIds, userID, userToken)
          rawProfile = mapReduceGenreScores(songInteractionScores, songProfilesMap)

          // [NEW] 在归一化之前，先将所有维度平移到正数区间
//          shiftedProfile = StatisticsUtils.shiftToPositive(rawProfile)

          // 归一化
          finalProfile = StatisticsUtils.normalizeVector(rawProfile)

          _ <- logInfo(s"计算出用户画像，包含 ${finalProfile.vector.length} 个曲风偏好")
          _ <- logInfo(s"原始Profile（可能含负数）: ${rawProfile.vector.take(5)}...")
          _ <- logInfo(s"最终归一化Profile: ${finalProfile.vector.take(5)}...")
        } yield finalProfile
      }
    } yield profile
  }

  // ... 其他方法 (mapReduceGenreScores, fetchGenresForSongs) 保持不变 ...

  private def mapReduceGenreScores(
                                    songScores: Map[String, Double],
                                    genreMap: Map[String, Profile]
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