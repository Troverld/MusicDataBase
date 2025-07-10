// ===== src/main/scala/Utils/SearchUtils.scala =====

// file: Utils/SearchUtils.scala
package Utils

import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import cats.effect.IO
import io.circe.generic.auto._
import Common.API.PlanContext
import cats.implicits._
import org.joda.time.DateTime

object SearchUtils {

  // ... [已有代码保持不变] ...
  def fetchAverageRating(songID: String)(using planContext: PlanContext): IO[(Double, Int)] = {
    val sql = s"SELECT AVG(rating) AS avg_rating, COUNT(rating) AS rating_count FROM ${schemaName}.song_rating WHERE song_id = ?"
    val params = List(SqlParameter("String", songID))

    readDBRows(sql, params).flatMap {
      case row :: Nil =>
        // 使用 for-comprehension 和 IO.fromEither 安全地解码，参考了范例的最佳实践
        for {
          avgRatingOpt <- IO.fromEither(row.hcursor.get[Option[Double]]("avgRating")
            .leftMap(err => new Exception(s"解码 avgRating 失败: ${err.getMessage}", err)))
          ratingCount <- IO.fromEither(row.hcursor.get[Int]("ratingCount")
            .leftMap(err => new Exception(s"解码 ratingCount 失败: ${err.getMessage}", err)))
        } yield (avgRatingOpt.getOrElse(0.0), ratingCount)
      case _ =>
        // 如果没有评分记录，则平均分和评分数都为0
        IO.pure((0.0, 0))
    }
  }

  def fetchPlayCount(songID: String)(using planContext: PlanContext): IO[Int] = {
    val sql = s"SELECT COUNT(*) FROM ${schemaName}.playback_log WHERE song_id = ?"
    readDBInt(sql, List(SqlParameter("String", songID)))
  }


  /**
   * (新增方法)
   * 批量获取多首歌曲的播放次数。
   *
   * @param songIDs 歌曲ID列表
   * @return 一个Map，键是歌曲ID，值是播放次数
   */
  def fetchBatchPlayCounts(songIDs: List[String])(using planContext: PlanContext): IO[Map[String, Int]] = {
    if (songIDs.isEmpty) {
      IO.pure(Map.empty)
    } else {
      // 注意：直接拼接ID到SQL中，请确保ID来源是可信的，以防止SQL注入。
      val idsInClause = songIDs.map(id => s"'$id'").mkString(",")
      val sql = s"SELECT song_id, COUNT(*) as play_count FROM ${schemaName}.playback_log WHERE song_id IN ($idsInClause) GROUP BY song_id"

      readDBRows(sql, List.empty).flatMap { rows =>
        rows.traverse { row =>
          for {
            songId <- IO.fromEither(row.hcursor.get[String]("songID")
              .leftMap(err => new Exception(s"批量解码 play_count.songID 失败: ${err.getMessage}", err)))
            playCount <- IO.fromEither(row.hcursor.get[Int]("playCount")
              .leftMap(err => new Exception(s"批量解码 play_count.playCount 失败: ${err.getMessage}", err)))
          } yield (songId, playCount)
        }.map(_.toMap)
      }
    }
  }

  /**
   * (新增方法)
   * 批量获取多首歌曲的平均分和评分次数。
   *
   * @param songIDs 歌曲ID列表
   * @return 一个Map，键是歌曲ID，值是(平均分, 评分次数)的元组
   */
  def fetchBatchAverageRatings(songIDs: List[String])(using planContext: PlanContext): IO[Map[String, (Double, Int)]] = {
    if (songIDs.isEmpty) {
      IO.pure(Map.empty)
    } else {
      // 注意：直接拼接ID到SQL中，请确保ID来源是可信的，以防止SQL注入。
      val idsInClause = songIDs.map(id => s"'$id'").mkString(",")
      val sql = s"SELECT song_id, AVG(rating) as avg_rating, COUNT(rating) as rating_count FROM ${schemaName}.song_rating WHERE song_id IN ($idsInClause) GROUP BY song_id"

      readDBRows(sql, List.empty).flatMap { rows =>
        rows.traverse { row =>
          for {
            songId <- IO.fromEither(row.hcursor.get[String]("songID")
              .leftMap(err => new Exception(s"批量解码 avg_rating.songID 失败: ${err.getMessage}", err)))
            avgRatingOpt <- IO.fromEither(row.hcursor.get[Option[Double]]("avgRating")
              .leftMap(err => new Exception(s"批量解码 avg_rating.avgRating 失败: ${err.getMessage}", err)))
            ratingCount <- IO.fromEither(row.hcursor.get[Int]("ratingCount")
              .leftMap(err => new Exception(s"批量解码 avg_rating.ratingCount 失败: ${err.getMessage}", err)))
          } yield (songId, (avgRatingOpt.getOrElse(0.0), ratingCount))
        }.map(_.toMap)
      }
    }
  }

  def fetchUserPlaybackHistory(userID: String)(using planContext: PlanContext): IO[List[String]] = {
    val sql = s"SELECT song_id FROM ${schemaName}.playback_log WHERE user_id = ?"
    val params = List(SqlParameter("String", userID))

    readDBRows(sql, params).flatMap { rows =>
      rows.traverse { row =>
        IO.fromEither(
          // 修正：根据规范，song_id 应当解码为 songID
          row.hcursor.get[String]("songID")
            .leftMap(err => new Exception(s"解码 playback_log.songID 失败: ${err.getMessage}", err))
        )
      }
    }
  }

  /**
   * (新增方法)
   * 从数据库获取用户最近播放的歌曲ID集合。
   *
   * @param userID 目标用户的ID。
   * @param limit  要获取的歌曲数量。
   * @return 一个包含最近播放歌曲ID的Set的IO。
   */
  def fetchRecentPlayedSongs(userID: String, limit: Int)(using planContext: PlanContext): IO[Set[String]] = {
    val sql = s"SELECT song_id FROM ${schemaName}.playback_log WHERE user_id = ? ORDER BY play_time DESC LIMIT ?"
    readDBRows(sql, List(SqlParameter("String", userID), SqlParameter("Int", limit.toString)))
      .flatMap { rows =>
        rows.traverse { row =>
          IO.fromEither(
            // 修正：根据规范，song_id 应当解码为 songID
            row.hcursor.get[String]("songID")
              .leftMap(err => new Exception(s"解码 playback_log.songID 失败: ${err.getMessage}", err))
          )
        }
      }.map(_.toSet)
  }

  def fetchUserRatingHistory(userID: String)(using planContext: PlanContext): IO[Map[String, Int]] = {
    val sql = s"SELECT song_id, rating FROM ${schemaName}.song_rating WHERE user_id = ?"
    val params = List(SqlParameter("String", userID))

    readDBRows(sql, params).flatMap { rows =>
      val decodedPairsIO = rows.traverse { row =>
        for {
          songId <- IO.fromEither(
            // 修正：根据规范，song_id 应当解码为 songID
            row.hcursor.get[String]("songID")
              .leftMap(err => new Exception(s"解码 song_rating.songID 失败: ${err.getMessage}", err))
          )
          rating <- IO.fromEither(
            row.hcursor.get[Int]("rating")
              .leftMap(err => new Exception(s"解码 song_rating.rating 失败: ${err.getMessage}", err))
          )
        } yield (songId, rating)
      }
      decodedPairsIO.map(_.toMap)
    }
  }

  def fetchUserPlayedSongIds(userID: String)(using planContext: PlanContext): IO[Set[String]] = {
    val sql = s"SELECT DISTINCT song_id FROM ${schemaName}.playback_log WHERE user_id = ?"
    val params = List(SqlParameter("String", userID))

    readDBRows(sql, params).flatMap { rows =>
      rows.traverse { row =>
        IO.fromEither(
          // 修正：根据规范，song_id 应当解码为 songID
          row.hcursor.get[String]("songID")
            .leftMap(err => new Exception(s"解码 playback_log.songID 失败: ${err.getMessage}", err))
        )
      }.map(_.toSet)
    }
  }

  def logPlayback(userID: String, songID: String)(using planContext: PlanContext): IO[Unit] = {
    for {
      now <- IO(new DateTime())
      logId <- IO(java.util.UUID.randomUUID().toString)
      sql = s"INSERT INTO ${schemaName}.playback_log (log_id, user_id, song_id, play_time) VALUES (?, ?, ?, ?)"
      params = List(
        SqlParameter("String", logId),
        SqlParameter("String", userID),
        SqlParameter("String", songID),
        SqlParameter("DateTime", now.getMillis.toString)
      )
      _ <- writeDB(sql, params)
    } yield ()
  }

  def fetchUserSongRating(userID: String, songID: String)(using planContext: PlanContext): IO[Option[Int]] = {
    val sql = s"SELECT rating FROM ${schemaName}.song_rating WHERE user_id = ? AND song_id = ?"
    val params = List(SqlParameter("String", userID), SqlParameter("String", songID))

    readDBRows(sql, params).flatMap {
      case row :: _ => IO.fromEither(row.hcursor.get[Int]("rating")).map(Some(_))
      case Nil => IO.pure(None)
    }
  }

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
}