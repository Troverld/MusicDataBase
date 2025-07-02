package Process

import Common.API.{API, PlanContext, TraceID}
import Common.DBAPI.{initSchema, writeDB}
import Common.ServiceUtils.schemaName
import Global.{DBConfig, GlobalVariables, ServerConfig}
import Process.ProcessUtils.server2DB
import cats.effect.IO
import io.circe.generic.auto.*

import java.util.UUID

object Init {
  def init(config: ServerConfig): IO[Unit] = {
    given PlanContext = PlanContext(traceID = TraceID(UUID.randomUUID().toString), 0)
    given DBConfig = server2DB(config)

    val program: IO[Unit] = for {
      _ <- IO(GlobalVariables.isTest = config.isTest)
      _ <- API.init(config.maximumClientConnection)
      _ <- Common.DBAPI.SwitchDataSourceMessage(projectName = Global.ServiceCenter.projectName).send
      _ <- initSchema(schemaName)

      /**
       * 艺术家表，包含艺术家的基本信息
       * artist_id: 艺术家的唯一ID
       * name: 艺术家名称
       * bio: 艺术家简介
       */
      _ <- writeDB(
        s"""
        CREATE TABLE IF NOT EXISTS "${schemaName}"."artist_table" (
            artist_id VARCHAR NOT NULL PRIMARY KEY,
            name TEXT NOT NULL,
            bio TEXT
        );
        """,
        List()
      )

      /**
       * 乐队表，包含乐队的基本信息
       * band_id: 乐队的唯一ID
       * name: 乐队名称
       * members: 乐队成员ID列表 (JSON存储格式)
       * bio: 乐队简介
       */
      _ <- writeDB(
        s"""
        CREATE TABLE IF NOT EXISTS "${schemaName}"."band_table" (
            band_id VARCHAR NOT NULL PRIMARY KEY,
            name TEXT NOT NULL,
            members TEXT NOT NULL DEFAULT '[]', -- 仍然保留 members 字段
            bio TEXT
        );
        """,
        List()
      )
    } yield ()

    program.handleErrorWith { err =>
      println(s"[Error] Process.Init.init 失败, 请检查 db-manager 是否启动及端口问题. Error: ${err.getMessage}")
      IO(err.printStackTrace())
    }
  }
}