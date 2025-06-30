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

      // -- 用户表，记录用户的基本信息、认证信息和令牌状态 --
      // user_id: 唯一标识用户的主键
      // account: 用户名，唯一，用于登录
      // password: 用户密码的哈希值
      // token: 当前有效的登录令牌
      // token_valid_until: 令牌的过期时间
      // invalid_time: [可选] 用于强制登出的时间戳，通常在 UserLogoutMessage 中设置
      _ <- writeDB(
        s"""
        CREATE TABLE IF NOT EXISTS "${schemaName}"."user_table" (
            user_id VARCHAR NOT NULL PRIMARY KEY,
            account TEXT NOT NULL UNIQUE,
            password TEXT NOT NULL,
            token TEXT,
            token_valid_until TIMESTAMP,
            invalid_time TIMESTAMP
        );
        """,
        List()
      )

      // -- 管理员表，只存储具有管理员权限的用户ID --
      // admin_id: 指向 user_table 的外键，表示该用户是管理员
      _ <- writeDB(
        s"""
        CREATE TABLE IF NOT EXISTS "${schemaName}"."admin_table" (
            admin_id VARCHAR NOT NULL PRIMARY KEY,
            CONSTRAINT fk_user
                FOREIGN KEY(admin_id)
                REFERENCES "${schemaName}"."user_table"(user_id)
                ON DELETE CASCADE
        );
        """,
        List()
      )

      // -- 艺术家认证请求表 --
      // request_id: 请求的唯一ID
      // user_id: 发起请求的用户ID
      // artist_id: 目标艺术家ID
      // certification: 认证材料
      // status: 申请状态 (Pending, Approved, Rejected)
      // created_at: 请求创建时间
      _ <- writeDB(
        s"""
        CREATE TABLE IF NOT EXISTS "${schemaName}"."artist_auth_request_table" (
            request_id VARCHAR NOT NULL PRIMARY KEY,
            user_id TEXT NOT NULL,
            artist_id TEXT NOT NULL,
            certification TEXT NOT NULL,
            status TEXT NOT NULL,
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        );
        """,
        List()
      )

      // -- 乐队认证请求表 --
      // request_id: 请求的唯一ID
      // user_id: 发起请求的用户ID
      // band_id: 目标乐队ID
      // certification: 认证材料
      // status: 申请状态 (Pending, Approved, Rejected)
      // created_at: 请求创建时间
      _ <- writeDB(
        s"""
        CREATE TABLE IF NOT EXISTS "${schemaName}"."band_auth_request_table" (
            request_id VARCHAR NOT NULL PRIMARY KEY,
            user_id TEXT NOT NULL,
            band_id TEXT NOT NULL,
            certification TEXT,
            status TEXT NOT NULL,
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
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