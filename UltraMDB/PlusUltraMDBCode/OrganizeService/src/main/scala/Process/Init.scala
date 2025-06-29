
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
            /** 乐队认证请求表，包含每个用户发起的乐队认证相关信息
       * request_id: 请求的唯一ID
       * user_id: 用户ID
       * band_id: 乐队ID
       * certification: 认证证据
       * status: 当前申请状态 (如待审、审核通过、审核失败)
       */
      _ <- writeDB(
        s"""
        CREATE TABLE IF NOT EXISTS "${schemaName}"."band_auth_request_table" (
            request_id VARCHAR NOT NULL PRIMARY KEY,
            user_id TEXT NOT NULL,
            band_id TEXT NOT NULL,
            certification TEXT,
            status TEXT NOT NULL
        );
         
        """,
        List()
      )
      /** 艺术家认证请求表
       * request_id: 认证请求的唯一ID
       * user_id: 用户ID
       * artist_id: 艺术家ID
       * certification: 认证证据
       * status: 当前申请状态 (如待审、审核通过、审核失败)
       */
      _ <- writeDB(
        s"""
        CREATE TABLE IF NOT EXISTS "${schemaName}"."artist_auth_request_table" (
            request_id VARCHAR NOT NULL PRIMARY KEY,
            user_id TEXT NOT NULL,
            artist_id TEXT NOT NULL,
            certification TEXT NOT NULL,
            status TEXT NOT NULL
        );
         
        """,
        List()
      )
      /** 管理员表，记录系统管理员的基本信息
       * admin_id: 管理员的唯一ID
       */
      _ <- writeDB(
        s"""
        CREATE TABLE IF NOT EXISTS "${schemaName}"."admin_table" (
            admin_id VARCHAR NOT NULL PRIMARY KEY
        );
         
        """,
        List()
      )
      /** 用户表，记录用户的基本信息和令牌失效时间
       * user_id: 唯一标识用户的主键
       * account: 用户名，用于用户登录
       * password: 用户的密码
       * invalid_time: 用户令牌的失效时间
       */
      _ <- writeDB(
        s"""
        CREATE TABLE IF NOT EXISTS "${schemaName}"."user_table" (
            user_id VARCHAR NOT NULL PRIMARY KEY,
            account TEXT NOT NULL,
            password TEXT NOT NULL,
            invalid_time TIMESTAMP
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
    