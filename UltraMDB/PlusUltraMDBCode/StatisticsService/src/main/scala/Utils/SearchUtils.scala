// file: Utils/SearchUtils.scala
package Utils

import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import cats.effect.IO
import io.circe.generic.auto._
import Common.API.PlanContext
import Common.Object.SqlParameter
import cats.implicits._
import Common.Object.SqlParameter
import Common.DBAPI.writeDB
import org.joda.time.DateTime

object SearchUtils {

  /**
   * 从数据库获取一首歌的平均评分和评分总数。
   */
  def fetchAverageRating(songID: String)(using planContext: PlanContext): IO[(Double, Int)] = {
    val sql = s"SELECT AVG(rating) AS avg_rating, COUNT(rating) AS rating_count FROM ${schemaName}.song_rating WHERE song_id = ?"
    val params = List(SqlParameter("String", songID))

    readDBRows(sql, params).flatMap {
      case row :: Nil =>
        // 确保使用正确的 camelCase 字段名来解码
        val avgResult = row.hcursor.get[Option[Double]]("avgRating").getOrElse(None)
        val countResult = row.hcursor.get[Int]("ratingCount").getOrElse(0)
        val averageRating = avgResult.getOrElse(0.0)
        IO.pure((averageRating, countResult))
      case _ =>
        IO.pure((0.0, 0))
    }
  }

  /**
   * 从数据库获取一首歌的播放总次数。
   */
  def fetchPlayCount(songID: String)(using planContext: PlanContext): IO[Int] = {
    val sql = s"SELECT COUNT(*) FROM ${schemaName}.playback_log WHERE song_id = ?"
    readDBInt(sql, List(SqlParameter("String", songID)))
  }

  /**
   * 从数据库获取一个用户的所有播放历史记录（仅歌曲ID）。
   *
   * @param userID 目标用户的ID。
   * @return 一个包含所有播放过的歌曲ID的列表的IO。
   */
  def fetchUserPlaybackHistory(userID: String)(using planContext: PlanContext): IO[List[String]] = {
    val sql = s"SELECT song_id FROM ${schemaName}.playback_log WHERE user_id = ?"
    val params = List(SqlParameter("String", userID))

    readDBRows(sql, params).flatMap { rows =>
      // 遍历每一行，并从JSON中解码 "songId" (camelCase) 字段。
      rows.traverse { row =>
        IO.fromEither(
          row.hcursor.get[String]("songId") // **修正：使用 camelCase "songId"**
            .leftMap(err => new Exception(s"解码 playback_log.songId 失败: ${err.getMessage}", err))
        )
      }
    }
  }

  /**
   * 从数据库获取一个用户的所有评分历史记录。
   *
   * @param userID 目标用户的ID。
   * @return 一个从歌曲ID映射到评分的Map的IO。
   */
  def fetchUserRatingHistory(userID: String)(using planContext: PlanContext): IO[Map[String, Int]] = {
    val sql = s"SELECT song_id, rating FROM ${schemaName}.song_rating WHERE user_id = ?"
    val params = List(SqlParameter("String", userID))

    readDBRows(sql, params).flatMap { rows =>
      // 遍历每一行，解码 "songId" 和 "rating" 字段，然后组合成元组。
      val decodedPairsIO = rows.traverse { row =>
        for {
          songId <- IO.fromEither(
            row.hcursor.get[String]("songId") // **修正：使用 camelCase "songId"**
              .leftMap(err => new Exception(s"解码 song_rating.songId 失败: ${err.getMessage}", err))
          )
          rating <- IO.fromEither(
            row.hcursor.get[Int]("rating") // `rating` 已经是 camelCase 格式
              .leftMap(err => new Exception(s"解码 song_rating.rating 失败: ${err.getMessage}", err))
          )
        } yield (songId, rating)
      }

      // 将元组列表转换为Map。
      decodedPairsIO.map(_.toMap)
    }
  }

  def fetchUserPlayedSongIds(userID: String)(using planContext: PlanContext): IO[Set[String]] = {
    val sql = s"SELECT DISTINCT song_id FROM ${schemaName}.playback_log WHERE user_id = ?"
    val params = List(SqlParameter("String", userID))

    readDBRows(sql, params).flatMap { rows =>
      rows.traverse { row =>
        IO.fromEither(
          row.hcursor.get[String]("songId") // 确保使用 camelCase
            .leftMap(err => new Exception(s"解码 playback_log.songId 失败: ${err.getMessage}", err))
        )
      }.map(_.toSet)
    }
  }

  /**
   * 向数据库中插入一条新的播放记录。
   * 封装了ID和时间戳的生成，以及数据库的写操作。
   *
   * @param userID 播放用户的ID。
   * @param songID 播放的歌曲ID。
   * @return 一个表示操作完成的 IO[Unit]。
   */
  def logPlayback(userID: String, songID: String)(using planContext: PlanContext): IO[Unit] = {
    for {
      // 在IO中生成业务数据
      now <- IO(new DateTime())
      logId <- IO(java.util.UUID.randomUUID().toString)

      // 定义SQL和参数
      sql = s"INSERT INTO ${schemaName}.playback_log (log_id, user_id, song_id, play_time) VALUES (?, ?, ?, ?)"
      params = List(
        SqlParameter("String", logId),
        SqlParameter("String", userID),
        SqlParameter("String", songID),
        SqlParameter("DateTime", now.getMillis.toString)
      )

      // 执行数据库写操作
      _ <- writeDB(sql, params)
    } yield ()
  }

    /**
   * 查询特定用户对特定歌曲的评分。
   * @return IO[Option[Int]]，如果找到评分则为 Some(rating)，否则为 None。
   */
  def fetchUserSongRating(userID: String, songID: String)(using planContext: PlanContext): IO[Option[Int]] = {
    val sql = s"SELECT rating FROM ${schemaName}.song_rating WHERE user_id = ? AND song_id = ?"
    val params = List(SqlParameter("String", userID), SqlParameter("String", songID))
    
    readDBRows(sql, params).flatMap {
      case row :: _ => // 只关心第一行（理论上也只应该有一行）
        IO.fromEither(row.hcursor.get[Int]("rating")).map(Some(_))
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
}