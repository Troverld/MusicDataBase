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
      
    } yield ()

    program.handleErrorWith(err => IO {
      println("[Error] Process.Init.init 失败, 请检查 db-manager 是否启动及端口问题")
      err.printStackTrace()
    })
  }
}