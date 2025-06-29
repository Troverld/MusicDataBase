
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
            /** 歌单表，存储歌单的基本信息和内容
       * collection_id: 歌单的唯一ID
       * name: 歌单名称
       * owner_id: 所有者用户ID
       * description: 歌单简介
       * contents: 包含歌曲ID列表 (JSON存储格式)
       * maintainers: 维护者用户ID列表 (JSON存储格式)
       * upload_time: 歌单创建或上传时间
       */
      _ <- writeDB(
        s"""
        CREATE TABLE IF NOT EXISTS "${schemaName}"."collection_table" (
            collection_id VARCHAR NOT NULL PRIMARY KEY,
            name TEXT NOT NULL,
            owner_id TEXT NOT NULL,
            description TEXT,
            contents TEXT,
            maintainers TEXT,
            upload_time TIMESTAMP NOT NULL
        );
         
        """,
        List()
      )
      /** 存储专辑的基础信息，包括名称、创作者、协作者及内容等。
       * album_id: 专辑的唯一ID，主键
       * name: 专辑名称
       * creators: 创作者ID列表，使用JSON存储格式
       * collaborators: 协作者ID列表，使用JSON存储格式
       * release_time: 专辑发布时间
       * description: 专辑简介
       * contents: 专辑包含的歌曲ID列表，使用JSON存储格式
       */
      _ <- writeDB(
        s"""
        CREATE TABLE IF NOT EXISTS "${schemaName}"."album_table" (
            album_id VARCHAR NOT NULL PRIMARY KEY,
            name TEXT NOT NULL,
            creators TEXT NOT NULL,
            collaborators TEXT,
            release_time TIMESTAMP NOT NULL,
            description TEXT,
            contents TEXT
        );
         
        """,
        List()
      )
      /** 播放列表表，存储播放集相关信息
       * playlist_id: 播放列表的唯一ID
       * owner_id: 用户ID
       * contents: 播放集歌曲ID列表（JSON存储格式）
       * current_song_id: 当前正在播放的歌曲ID
       * current_position: 当前播放歌曲的位置
       * play_mode: 播放方式（如随机播放、单曲循环等）
       */
      _ <- writeDB(
        s"""
        CREATE TABLE IF NOT EXISTS "${schemaName}"."playlist_table" (
            playlist_id VARCHAR NOT NULL PRIMARY KEY,
            owner_id TEXT NOT NULL,
            contents TEXT,
            current_song_id TEXT,
            current_position INT DEFAULT 0,
            play_mode TEXT DEFAULT 'Next'
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
    