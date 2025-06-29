
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
            /** SongTable，记录歌曲的详细信息
       * song_id: 歌曲的唯一ID，主键
       * name: 歌曲名称
       * release_time: 歌曲的发布时间
       * creators: 创作者ID列表，存储为JSON格式
       * performers: 演唱者ID列表，存储为JSON格式
       * lyricists: 作词者ID列表，存储为JSON格式
       * composers: 作曲者ID列表，存储为JSON格式
       * arrangers: 编曲者ID列表，存储为JSON格式
       * instrumentalists: 演奏者ID列表，存储为JSON格式
       * genres: 曲风ID列表，存储为JSON格式
       * uploader_id: 上传者用户ID
       */
      _ <- writeDB(
        s"""
        CREATE TABLE IF NOT EXISTS "${schemaName}"."song_table" (
            song_id VARCHAR NOT NULL PRIMARY KEY,
            name TEXT NOT NULL,
            release_time TIMESTAMP NOT NULL,
            creators TEXT NOT NULL DEFAULT '[]',
            performers TEXT NOT NULL DEFAULT '[]',
            lyricists TEXT NOT NULL DEFAULT '[]',
            composers TEXT NOT NULL DEFAULT '[]',
            arrangers TEXT NOT NULL DEFAULT '[]',
            instrumentalists TEXT NOT NULL DEFAULT '[]',
            genres TEXT NOT NULL DEFAULT '[]',
            uploader_id TEXT NOT NULL
        );
         
        """,
        List()
      )
      /** 曲风表，包含曲风的基本信息
       * genre_id: 曲风的唯一ID
       * name: 曲风名称
       * description: 曲风简介
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
    } yield ()

    program.handleErrorWith(err => IO {
      println("[Error] Process.Init.init 失败, 请检查 db-manager 是否启动及端口问题")
      err.printStackTrace()
    })
  }
}
    