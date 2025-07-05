package Impl

import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import APIs.OrganizeService.validateUserMapping
import APIs.MusicService.GetSongProfile
import Objects.StatisticsService.{Dim, Profile} // 1. 导入 Dim
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
  private val ratingWeightCoefficient = 10.0

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

  /**
   * 验证用户身份
   */
  private def validateUser()(using PlanContext): IO[Unit] = {
    logInfo("正在验证用户身份") >>
      validateUserMapping(userID, userToken).send.flatMap {
        case (true, _) => logInfo("用户身份验证通过")
        case (false, message) => IO.raiseError(new IllegalArgumentException(s"用户身份验证失败: $message"))
      }
  }

  /**
   * 核心方法：实时计算并归一化用户画像
   */
  private def calculateAndNormalizeProfile()(using PlanContext): IO[Profile] = {
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
          val ratingBonus = ratedSongsMap.get(songId).map((_ - 3.0)*ratingWeightCoefficient).getOrElse(0.0)
          val interactionScore = 1.0 + ratingBonus
          (songId, interactionScore)
        }.toMap
        
        for {
          genresForSongsMap <- fetchGenresForSongs(allInteractedSongIds)
          
          rawGenrePreferences = songInteractionScores.toList.foldLeft(Map.empty[String, Double]) {
            case (acc, (songId, score)) =>
              genresForSongsMap.getOrElse(songId, List.empty).foldLeft(acc) {
                case (innerAcc, genreId) =>
                  innerAcc.updated(genreId, innerAcc.getOrElse(genreId, 0.0) + score)
              }
          }
          
          positivePreferences = rawGenrePreferences.toList.filter(_._2 > 0)
          
          // 2. 修正点: 将元组列表转换为 Dim 对象列表
          preferenceDims = positivePreferences.map { case (genreId, score) =>
            Dim(genreId, score)
          }
          
          // 使用修正后的列表创建 Profile
          rawProfile = Profile(vector = preferenceDims, norm = false)
          finalProfile = StatisticsUtils.normalizeVector(rawProfile)
          
          _ <- logInfo(s"计算出用户画像，包含 ${finalProfile.vector.length} 个曲风偏好")
        } yield finalProfile
      }
    } yield profile
  }

  /**
   * 获取用户的播放历史（歌曲ID列表）
   */
  private def fetchUserPlaybackHistory()(using PlanContext): IO[List[String]] = {
    val sql = s"SELECT song_id FROM ${schemaName}.playback_log WHERE user_id = ?"
    readDBRows(sql, List(SqlParameter("String", userID))).map(_.map(decodeField[String](_, "song_id")))
  }

  /**
   * 获取用户的评分历史（SongID -> Rating 的映射）
   */
  private def fetchUserRatingHistory()(using PlanContext): IO[Map[String, Int]] = {
    val sql = s"SELECT song_id, rating FROM ${schemaName}.song_rating WHERE user_id = ?"
    readDBRows(sql, List(SqlParameter("String", userID))).map { rows =>
      rows.map { row =>
        decodeField[String](row, "song_id") -> decodeField[Int](row, "rating")
      }.toMap
    }
  }

  /**
   * 为一批歌曲获取其所属的曲风列表，通过并行调用GetSongProfile API实现。
   */
  private def fetchGenresForSongs(songIds: Set[String])(using PlanContext): IO[Map[String, List[String]]] = {
    if (songIds.isEmpty) return IO.pure(Map.empty)
    
    logInfo(s"准备为 ${songIds.size} 首歌曲并行获取曲风Profile")
    
    songIds.toList.parTraverse { songId =>
      GetSongProfile(userID, userToken, songId).send.map {
        case (Some(profile), _) =>
          // 3. 修正点: 使用 .GenreID 提取曲风ID
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