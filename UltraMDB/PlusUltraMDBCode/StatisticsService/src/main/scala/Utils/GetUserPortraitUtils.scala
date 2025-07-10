package Utils

import Common.API.PlanContext
import APIs.MusicService.{GetMultSongsProfiles, GetSongProfile}
import Objects.StatisticsService.{Dim, Profile}
import cats.effect.IO
import cats.implicits._
import java.io.{FileWriter, PrintWriter}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import io.circe.generic.auto._
import cats.effect.unsafe.implicits.global

object GetUserPortraitUtils {

  // 日志文件写入器：自动追加 + 自动 flush
  private val logFile = new FileWriter("output.txt", true)
  private val writer = new PrintWriter(logFile, true)

  private val timestampFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

  private def logInfo(message: String)(using pc: PlanContext): IO[Unit] = IO {
    val timestamp = LocalDateTime.now().format(timestampFmt)
    val tid = pc.traceID.id
    writer.println(s"[$timestamp] INFO TID=$tid -- $message")
  }

  private def logWarn(message: String)(using pc: PlanContext): IO[Unit] = IO {
    val timestamp = LocalDateTime.now().format(timestampFmt)
    val tid = pc.traceID.id
    writer.println(s"[$timestamp] WARN TID=$tid -- $message")
  }

  def generateUserProfile(userID: String, userToken: String)(using planContext: PlanContext): IO[Profile] = {
    val ratingWeightCoefficient = 10.0

    for {
      _ <- logInfo("开始实时计算用户画像")

      playedSongs    <- SearchUtils.fetchUserPlaybackHistory(userID)
      ratedSongsMap  <- SearchUtils.fetchUserRatingHistory(userID)

      allInteractedSongIds = (playedSongs ++ ratedSongsMap.keys).toSet
      _ <- logInfo(s"用户总共交互过 ${allInteractedSongIds.size} 首歌曲")

      profile <- if (allInteractedSongIds.isEmpty) {
        logInfo("用户暂无交互记录，返回空画像") >>
          IO.pure(Profile(List.empty, norm = true))
      } else {
        val songInteractionScores = allInteractedSongIds.map { songId =>
          val ratingBonus = ratedSongsMap.get(songId).map(r => (r - 3.0) * ratingWeightCoefficient).getOrElse(0.0)
          val interactionScore = 1.0 + ratingBonus
          songId -> interactionScore
        }.toMap

        for {
          songProfilesMap <- fetchGenresForSongs(allInteractedSongIds, userID, userToken)
          rawProfile       = mapReduceGenreScores(songInteractionScores, songProfilesMap)
          finalProfile     = StatisticsUtils.normalizeVector(rawProfile)
          _               <- logInfo(s"计算出用户画像，包含 ${finalProfile.vector.length} 个曲风偏好")
        } yield finalProfile
      }
    } yield profile
  }

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

    logInfo(s"准备批量获取 ${songIds.size} 首歌曲的曲风Profile") >>
      GetMultSongsProfiles(userID, userToken, songIds.toList).send.flatMap {
        case (Some(profiles), _) =>
          IO.pure(songIds.toList.zip(profiles).toMap)

        case (None, message) =>
          logWarn(s"批量获取歌曲Profile失败: $message. 将回退到单曲获取方式") >>
            songIds.toList.traverse { songId =>
              GetSongProfile(userID, userToken, songId).send.map {
                case (Some(profile), _) => songId -> profile
                case (None, msg) =>
                  logWarn(s"获取歌曲 $songId 的Profile失败: $msg. 该歌曲的曲风贡献将为空。").unsafeRunSync()
                  songId -> Profile(List.empty, norm = true)
              }
            }.map(_.toMap)
      }
  }
}
