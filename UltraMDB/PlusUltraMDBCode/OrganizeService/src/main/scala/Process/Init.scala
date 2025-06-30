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
import Utils.CryptoUtils // 引入我们的加密工具

object Init {
  def init(config: ServerConfig): IO[Unit] = {
    given PlanContext = PlanContext(traceID = TraceID(UUID.randomUUID().toString), 0)
    given DBConfig = server2DB(config)

    // 定义初始管理员信息
    val initialAdminId = "00000000-0000-0000-0000-000000000001" // 使用一个固定的、可识别的UUID
    val initialAdminUsername = "admin"
    val initialAdminPassword = "admin123" // 在生产环境中应使用更安全的密码，或从安全配置中读取

    val program: IO[Unit] = for {
      _ <- IO(GlobalVariables.isTest=config.isTest)
      _ <- API.init(config.maximumClientConnection)
      _ <- Common.DBAPI.SwitchDataSourceMessage(projectName = Global.ServiceCenter.projectName).send
      _ <- initSchema(schemaName)

      // -- 用户表 --
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

      // -- 管理员表 --
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

      // -- 插入初始管理员数据 --
      _ <- IO.println("正在检查并创建初始管理员账户...")
      // 1. 加密初始密码
      encryptedPassword = CryptoUtils.encryptPassword(initialAdminPassword)

      // 2. 插入到 user_table。ON CONFLICT (account) DO NOTHING 确保如果该用户名的用户已存在，则什么也不做。
      _ <- writeDB(
        s"""
        INSERT INTO "${schemaName}"."user_table" (user_id, account, password)
        VALUES (?, ?, ?)
        ON CONFLICT (account) DO NOTHING;
        """,
        List(
          Common.Object.SqlParameter("String", initialAdminId),
          Common.Object.SqlParameter("String", initialAdminUsername),
          Common.Object.SqlParameter("String", encryptedPassword)
        )
      )

      // 3. 插入到 admin_table。ON CONFLICT (admin_id) DO NOTHING 确保如果该管理员记录已存在，则什么也不做。
      _ <- writeDB(
        s"""
        INSERT INTO "${schemaName}"."admin_table" (admin_id)
        VALUES (?)
        ON CONFLICT (admin_id) DO NOTHING;
        """,
        List(
          Common.Object.SqlParameter("String", initialAdminId)
        )
      )
      _ <- IO.println("初始管理员账户设置完成。")


    } yield ()

    program.handleErrorWith(err => IO {
      println("[Error] Process.Init.init 失败, 请检查 db-manager 是否启动及端口问题")
      err.printStackTrace()
    })
  }
}