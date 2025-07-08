package Utils

import Common.API.PlanContext
import Common.DBAPI.{readDBInt, readDBRows, writeDB}
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import cats.effect.IO
import cats.implicits._
import org.joda.time.DateTime

/**
 * 数据访问对象 (DAO) / 仓库 (Repository)
 * 统一封装所有与数据库的直接交互操作，并遵循项目的数据转换和命名约定。
 * 核心约定: 数据库列名的 snake_case 在解码时必须使用 `*ID` (ID大写) 形式的 camelCase。
 */
object SearchUtils {

  // ===================================================================================
  // --- 读/写 操作 ---
  // ===================================================================================

  /**
   * 查询特定用户对特定歌曲的评分。
   */
  def fetchUserSongRating(userID: String, songID: String)(using planContext: PlanContext): IO[Option[Int]] = {
    val sql = s"SELECT rating FROM ${schemaName}.song_rating WHERE user_id = ? AND song_id = ?"
    val params = List(SqlParameter("String", userID), SqlParameter("String", songID))
    
    readDBRows(sql, params).flatMap {
      case row :: _ =>
        IO.fromEither(row.hcursor.get[Int]("rating")
          .leftMap(err => new Exception(s"解码 song_rating.rating 失败: ${err.getMessage}", err)))
          .map(Some(_))
      case Nil =>
        IO.pure(None)
    }
  }

  /**
   * 更新用户对歌曲的评分。
   */
  def updateUserSongRating(userID: String, songID: String, rating: Int)(using planContext: PlanContext): IO[Unit] = {
    val sql = s"UPDATE ${schemaName}.song_rating SET rating = ?, rated_at = ? WHERE user_id = ? AND song_id = ?"
    for {
      now <- IO(new DateTime())
      _ <- writeDB(sql, List(
        SqlParameter("Int", rating.toString),
        SqlParameter("DateTime", now.getMillis.toString),
        SqlParameter("String", userID),
        SqlParameter("String", songID)
      ))
    } yield ()
  }
  
  /**
   * 插入一条新的用户歌曲评分记录。
   */
  def insertUserSongRating(userID: String, songID: String, rating: Int)(using planContext: PlanContext): IO[Unit] = {
    val sql = s"INSERT INTO ${schemaName}.song_rating (user_id, song_id, rating, rated_at) VALUES (?, ?, ?, ?)"
    for {
      now <- IO(new DateTime())
      _ <- writeDB(sql, List(
        SqlParameter("String", userID),
        SqlParameter("String", songID),
        SqlParameter("Int", rating.toString),
        SqlParameter("DateTime", now.getMillis.toString)
      ))
    } yield ()
  }

  /**
   * 查询一首歌的平均评分和评分总数。
   */
  def fetchAverageRating(songID: String)(using planContext: PlanContext): IO[(Double, Int)] = {
    val sql = s"SELECT AVG(rating) AS avg_rating, COUNT(rating) AS rating_count FROM ${schemaName}.song_rating WHERE song_id = ?"
    val params = List(SqlParameter("String", songID))

    readDBRows(sql, params).flatMap {
      case row :: Nil =>
        for {
          avgRatingOpt <- IO.fromEither(row.hcursor.get[Option[Double]]("avgRating")
            .leftMap(err => new Exception(s"解码 avg_rating 失败: ${err.getMessage}", err)))
          ratingCount <- IO.fromEither(row.hcursor.get[Int]("ratingCount")
            .leftMap(err => new Exception(s"解码 rating_count 失败: ${err.getMessage}", err)))
        } yield (avgRatingOpt.getOrElse(0.0), ratingCount)
      case _ =>
        IO.pure((0.0, 0))
    }
  }

  /**
   * 获取歌曲播放次数。
   */
  def fetchPlayCount(songID: String)(using planContext: PlanContext): IO[Int] = {
    val sql = s"SELECT COUNT(*) FROM ${schemaName}.playback_log WHERE song_id = ?"
    readDBInt(sql, List(SqlParameter("String", songID)))
  }
  
  /**
   * 向数据库中插入一条新的播放记录。
   */
  def logPlayback(userID: String, songID: String)(using planContext: PlanContext): IO[Unit] = {
    val sql = s"INSERT INTO ${schemaName}.playback_log (log_id, user_id, song_id, play_time) VALUES (?, ?, ?, ?)"
    for {
      now   <- IO(new DateTime())
      // **最终修正**: 严格使用 `logID`
      logID <- IO(java.util.UUID.randomUUID().toString)
      _ <- writeDB(sql, List(
        SqlParameter("String", logID),
        SqlParameter("String", userID),
        SqlParameter("String", songID),
        SqlParameter("DateTime", now.getMillis.toString)
      ))
    } yield ()
  }

  // ===================================================================================
  // --- 纯粹的读操作 ---
  // ===================================================================================

  /**
   * 从数据库获取一个用户的所有播放历史记录（仅歌曲ID）。
   */
  def fetchUserPlaybackHistory(userID: String)(using planContext: PlanContext): IO[List[String]] = {
    val sql = s"SELECT song_id FROM ${schemaName}.playback_log WHERE user_id = ?"
    val params = List(SqlParameter("String", userID))
    readDBRows(sql, params).flatMap { rows =>
      rows.traverse { row =>
        IO.fromEither(
          // **最终修正**: 严格使用 `"songID"`
          row.hcursor.get[String]("songID")
            .leftMap(err => new Exception(s"解码 playback_log.songID 失败: ${err.getMessage}", err))
        )
      }
    }
  }

  /**
   * 从数据库获取一个用户的所有评分历史记录。
   */
  def fetchUserRatingHistory(userID: String)(using planContext: PlanContext): IO[Map[String, Int]] = {
    val sql = s"SELECT song_id, rating FROM ${schemaName}.song_rating WHERE user_id = ?"
    val params = List(SqlParameter("String", userID))
    readDBRows(sql, params).flatMap { rows =>
      val decodedPairsIO = rows.traverse { row =>
        for {
          // **最终修正**: 严格使用 `"songID"` 和局部变量 `songID`
          songID <- IO.fromEither(row.hcursor.get[String]("songID")
            .leftMap(err => new Exception(s"解码 song_rating.songID 失败: ${err.getMessage}", err)))
          rating <- IO.fromEither(row.hcursor.get[Int]("rating")
            .leftMap(err => new Exception(s"解码 song_rating.rating 失败: ${err.getMessage}", err)))
        } yield (songID, rating)
      }
      decodedPairsIO.map(_.toMap)
    }
  }
  
  /**
   * 从数据库获取一个用户所有播放过的歌曲ID集合。
   */
  def fetchUserPlayedSongIds(userID: String)(using planContext: PlanContext): IO[Set[String]] = {
    val sql = s"SELECT DISTINCT song_id FROM ${schemaName}.playback_log WHERE user_id = ?"
    val params = List(SqlParameter("String", userID))
    readDBRows(sql, params).flatMap { rows =>
      rows.traverse { row =>
        IO.fromEither(
          // **最终修正**: 严格使用 `"songID"`
          row.hcursor.get[String]("songID")
            .leftMap(err => new Exception(s"解码 playback_log.songID 失败: ${err.getMessage}", err))
        )
      }.map(_.toSet)
    }
  }
}