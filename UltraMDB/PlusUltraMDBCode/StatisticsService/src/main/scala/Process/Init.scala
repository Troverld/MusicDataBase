package Process

import Common.API.{API, PlanContext, TraceID}
import Common.DBAPI.{initSchema, writeDB}
import Common.ServiceUtils.schemaName
import Global.ServerConfig
import cats.effect.IO
import io.circe.generic.auto.*
import java.util.UUID
import Global.DBConfig
import Process.ProcessUtils.server2DB
import Global.GlobalVariables

object Init {
  def init(config: ServerConfig): IO[Unit] = {
    given PlanContext = PlanContext(traceID = TraceID(UUID.randomUUID().toString), 0)
    given DBConfig = server2DB(config)

    val program: IO[Unit] = for {
      _ <- IO(GlobalVariables.isTest=config.isTest)
      _ <- API.init(config.maximumClientConnection)
      _ <- Common.DBAPI.SwitchDataSourceMessage(projectName = Global.ServiceCenter.projectName).send
      _ <- initSchema(schemaName)
      
      /** 播放记录表，记录用户的歌曲播放行为
       * log_id: 播放记录的唯一ID
       * user_id: 播放用户的ID
       * song_id: 播放歌曲的ID
       * play_time: 播放时间戳
       */
      _ <- writeDB(
        s"""
        CREATE TABLE IF NOT EXISTS "${schemaName}"."playback_log" (
            log_id VARCHAR NOT NULL PRIMARY KEY,
            user_id TEXT NOT NULL,
            song_id TEXT NOT NULL,
            play_time TIMESTAMP NOT NULL
        );
        """,
        List()
      )
      
      /** 歌曲评分表，记录用户对歌曲的评分
       * user_id: 评分用户的ID
       * song_id: 被评分歌曲的ID
       * rating: 评分值(1-5)
       * rated_at: 评分时间
       */
      _ <- writeDB(
        s"""
        CREATE TABLE IF NOT EXISTS "${schemaName}"."song_rating" (
            user_id TEXT NOT NULL,
            song_id TEXT NOT NULL,
            rating INT NOT NULL CHECK (rating >= 1 AND rating <= 5),
            rated_at TIMESTAMP NOT NULL,
            PRIMARY KEY (user_id, song_id)
        );
        """,
        List()
      )
      
      /** 用户画像缓存表，存储计算好的用户偏好数据
       * user_id: 用户ID
       * genre_id: 曲风ID
       * preference_score: 偏好度分数
       * updated_at: 更新时间
       */
      _ <- writeDB(
        s"""
        CREATE TABLE IF NOT EXISTS "${schemaName}"."user_portrait_cache" (
            user_id TEXT NOT NULL,
            genre_id TEXT NOT NULL,
            preference_score DOUBLE PRECISION NOT NULL,
            updated_at TIMESTAMP NOT NULL,
            PRIMARY KEY (user_id, genre_id)
        );
        """,
        List()
      )
      
      /** 歌曲热度缓存表，存储计算好的歌曲热度值
       * song_id: 歌曲ID
       * popularity_score: 热度分数
       * play_count: 播放次数
       * avg_rating: 平均评分
       * rating_count: 评分人数
       * updated_at: 更新时间
       */
      _ <- writeDB(
        s"""
        CREATE TABLE IF NOT EXISTS "${schemaName}"."song_popularity_cache" (
            song_id TEXT NOT NULL PRIMARY KEY,
            popularity_score DOUBLE PRECISION NOT NULL,
            play_count INT DEFAULT 0,
            avg_rating DOUBLE PRECISION DEFAULT 0.0,
            rating_count INT DEFAULT 0,
            updated_at TIMESTAMP NOT NULL
        );
        """,
        List()
      )
      
      /** 歌曲-曲风映射表，存储歌曲与曲风的多对多关系
       * song_id: 歌曲ID
       * genre_id: 曲风ID
       */
      _ <- writeDB(
        s"""
        CREATE TABLE IF NOT EXISTS "${schemaName}"."song_genre_mapping" (
            song_id TEXT NOT NULL,
            genre_id TEXT NOT NULL,
            PRIMARY KEY (song_id, genre_id)
        );
        """,
        List()
      )
      
      /** 歌曲表的基本结构（如果不存在的话）
       * 注意：这个表应该由 MusicService 创建，这里只是为了确保存在
       */
      _ <- writeDB(
        s"""
        CREATE TABLE IF NOT EXISTS "${schemaName}"."song_table" (
            song_id VARCHAR NOT NULL PRIMARY KEY,
            name TEXT NOT NULL,
            release_time TIMESTAMP NOT NULL,
            creators TEXT,
            performers TEXT,
            lyricists TEXT,
            arrangers TEXT,
            instrumentalists TEXT,
            composers TEXT,
            uploader_id TEXT NOT NULL
        );
        """,
        List()
      )

      /** 曲风表的基本结构（如果不存在的话）
       * 注意：这个表应该由 MusicService 创建，这里只是为了确保存在
       */
      _ <- writeDB(
        s"""
        CREATE TABLE IF NOT EXISTS "${schemaName}"."genre_table" (
            genre_id VARCHAR NOT NULL PRIMARY KEY,
            name TEXT NOT NULL,
            description TEXT
        );
        """,
        List()
      )

      /** 用户表的基本结构（如果不存在的话）
       * 注意：这个表应该由 OrganizeService 创建，这里只是为了确保存在
       */
      _ <- writeDB(
        s"""
        CREATE TABLE IF NOT EXISTS "${schemaName}"."user_table" (
            user_id VARCHAR NOT NULL PRIMARY KEY,
            account TEXT NOT NULL UNIQUE,
            password TEXT NOT NULL
        );
        """,
        List()
      )

      // 创建索引以提高查询性能
      _ <- writeDB(
        s"""
        CREATE INDEX IF NOT EXISTS idx_playback_log_user_id ON "${schemaName}"."playback_log" (user_id);
        CREATE INDEX IF NOT EXISTS idx_playback_log_song_id ON "${schemaName}"."playback_log" (song_id);
        CREATE INDEX IF NOT EXISTS idx_playback_log_play_time ON "${schemaName}"."playback_log" (play_time);
        CREATE INDEX IF NOT EXISTS idx_song_rating_song_id ON "${schemaName}"."song_rating" (song_id);
        CREATE INDEX IF NOT EXISTS idx_user_portrait_cache_user_id ON "${schemaName}"."user_portrait_cache" (user_id);
        CREATE INDEX IF NOT EXISTS idx_creator_stats_cache_creator ON "${schemaName}"."creator_stats_cache" (creator_id, creator_type);
        """,
        List()
      )
      
    } yield ()

    program.handleErrorWith(err => IO {
      println("[Error] Process.Init.init 失败, 请检查 db-manager 是否启动及端口问题")
      err.printStackTrace()
    })
  }
}