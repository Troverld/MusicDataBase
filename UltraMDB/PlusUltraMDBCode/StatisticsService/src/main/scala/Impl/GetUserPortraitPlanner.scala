package Impl

import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import APIs.OrganizeService.validateUserMapping
import APIs.MusicService.GetSongProfile
import Objects.StatisticsService.{Dim, Profile}
import Utils.StatisticsUtils
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory

/**
 * Planner for GetUserPortrait: 获取用户的音乐偏好画像
 *
 * @param userID      目标用户的ID
 * @param userToken   用户认证令牌
 * @param planContext 执行上下文
 */
case class GetUserPortraitPlanner(
                                   userID: String,
                                   userToken: String,
                                   override val planContext: PlanContext
                                 ) extends Planner[(Option[Profile], String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Option[Profile], String)] = {
    val logic: IO[Profile] = for {
      _ <- logInfo(s"开始获取用户 ${userID} 的音乐偏好画像")
      _ <- validateUser()
      profile <- calculateAndNormalizeProfile()
    } yield profile

    logic.map { profile =>
      (Some(profile), "获取用户画像成功")
    }.handleErrorWith { error =>
      logError(s"获取用户 ${userID} 画像失败", error) >>
        IO.pure((None, error.getMessage))
    }
  }

  private def validateUser()(using PlanContext): IO[Unit] = {
    logInfo("正在验证用户身份") >>
      validateUserMapping(userID, userToken).send.flatMap {
        case (true, _) => logInfo("用户身份验证通过")
        case (false, message) => IO.raiseError(new IllegalArgumentException(s"用户身份验证失败: $message"))
      }
  }

  private def calculateAndNormalizeProfile()(using PlanContext): IO[Profile] = {
    // 权重系数，方便调整
    val ratingWeightCoefficient = 10.0

    for {
      _ <- logInfo("开始实时计算用户画像")

      histories <- (fetchUserPlaybackHistory(), fetchUserRatingHistory()).parTupled
      (playedSongs, ratedSongsMap) = histories

      allInteractedSongIds = (playedSongs ++ ratedSongsMap.keys).toSet
      _ <- logInfo(s"用户总共交互过 ${allInteractedSongIds.size} 首歌曲")

      profile <- if (allInteractedSongIds.isEmpty) {
        logInfo("用户暂无交互记录，返回空画像") >>
          IO.pure(Profile(List.empty, norm = true))
      } else {
        val songInteractionScores = allInteractedSongIds.map { songId =>
          // **修正点 2: 放弃占位符，使用显式匿名函数**
          val ratingBonus = ratedSongsMap.get(songId).map { rating =>
            (rating - 3.0) * ratingWeightCoefficient
          }.getOrElse(0.0)

          val interactionScore = 1.0 + ratingBonus
          (songId, interactionScore)
        }.toMap

        for {
          genresForSongsMap <- fetchGenresForSongs(allInteractedSongIds)

          // **修正点 1: 重构嵌套的 foldLeft 以提高可读性和类型安全性**
          // Map Phase: 将 (歌曲ID, 分数) 映射为扁平化的 (曲风ID, 分数) 列表
          genreScorePairs = songInteractionScores.toList.flatMap {
            case (songId, score) =>
              val genres = genresForSongsMap.getOrElse(songId, List.empty)
              genres.map(genreId => (genreId, score))
          }

          // Reduce Phase: 按曲风ID分组，并对分数求和
          rawGenrePreferences = genreScorePairs
            .groupBy { case (genreId, _) => genreId }
            .view
            .mapValues { listOfPairs =>
              listOfPairs.map { case (_, score) => score }.sum
            }
            .toMap

          positivePreferences = rawGenrePreferences.toList.filter(_._2 > 0)

          preferenceDims = positivePreferences.map { case (genreId, score) =>
            Dim(genreId, score)
          }

          rawProfile = Profile(vector = preferenceDims, norm = false)
          finalProfile = StatisticsUtils.normalizeVector(rawProfile)

          _ <- logInfo(s"计算出用户画像，包含 ${finalProfile.vector.length} 个曲风偏好")
        } yield finalProfile
      }
    } yield profile
  }

  private def fetchUserPlaybackHistory()(using PlanContext): IO[List[String]] = {
    val sql = s"SELECT song_id FROM ${schemaName}.playback_log WHERE user_id = ?"
    readDBRows(sql, List(SqlParameter("String", userID))).map(_.map(decodeField[String](_, "song_id")))
  }

  private def fetchUserRatingHistory()(using PlanContext): IO[Map[String, Int]] = {
    val sql = s"SELECT song_id, rating FROM ${schemaName}.song_rating WHERE user_id = ?"
    readDBRows(sql, List(SqlParameter("String", userID))).map { rows =>
      rows.map { row =>
        decodeField[String](row, "song_id") -> decodeField[Int](row, "rating")
      }.toMap
    }
  }

  private def fetchGenresForSongs(songIds: Set[String])(using PlanContext): IO[Map[String, List[String]]] = {
    if (songIds.isEmpty) return IO.pure(Map.empty)

    logInfo(s"准备为 ${songIds.size} 首歌曲并行获取曲风Profile")

    songIds.toList.parTraverse { songId =>
      GetSongProfile(userID, userToken, songId).send.map {
        case (Some(profile), _) =>
          songId -> profile.vector.map(_.GenreID)
        case (None, message) =>
          logger.warn(s"TID=${planContext.traceID.id} -- 获取歌曲 $songId 的Profile失败: $message. 该歌曲的曲风贡献将为空。")
          songId -> List.empty[String]
      }
    }.map(_.toMap)
  }

  private def logInfo(message: String): IO[Unit] =
    IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))

  private def logError(message: String, cause: Throwable): IO[Unit] =
    IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}