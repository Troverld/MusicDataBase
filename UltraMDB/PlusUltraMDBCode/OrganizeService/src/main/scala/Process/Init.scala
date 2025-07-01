package Process

import Common.API.{API, PlanContext, TraceID}
import Common.DBAPI.{initSchema, writeDB}
import Common.Object.SqlParameter // Explicitly import SqlParameter for clarity
import Common.ServiceUtils.schemaName
import Global.{DBConfig, GlobalVariables, ServerConfig}
import Process.ProcessUtils.server2DB
import Utils.CryptoUtils
import cats.effect.IO
import java.util.UUID
import io.circe.generic.auto.deriveEncoder

object Init {
  def init(config: ServerConfig): IO[Unit] = {
    given PlanContext = PlanContext(traceID = TraceID(UUID.randomUUID().toString), 0)
    given DBConfig = server2DB(config)

    // --- Preserved: Initial administrator account details ---
    val initialAdminId = "00000000-0000-0000-0000-000000000001"
    val initialAdminUsername = "admin"
    // In a real production environment, this should come from a secure configuration source.
    val initialAdminPassword = "admin123"

    val program: IO[Unit] = for {
      _ <- IO(GlobalVariables.isTest = config.isTest)
      _ <- API.init(config.maximumClientConnection)
      _ <- Common.DBAPI.SwitchDataSourceMessage(projectName = Global.ServiceCenter.projectName).send
      _ <- initSchema(schemaName)
      _ <- IO.println("Schema initialized. Creating/Updating tables...")

      // -- Preserved: User Table (No changes needed) --
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

      // -- Preserved: Admin Table (No changes needed) --
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

      // --- [REFACTORED] Unified Authentication Request Table ---
      // This single table replaces the separate artist_auth_request_table and band_auth_request_table.
      _ <- writeDB(
        s"""
        CREATE TABLE IF NOT EXISTS "${schemaName}"."auth_request_table" (
            request_id VARCHAR NOT NULL PRIMARY KEY,
            user_id VARCHAR NOT NULL,
            target_id VARCHAR NOT NULL,      -- The ID of the artist OR band
            target_type VARCHAR NOT NULL,    -- Stores "Artist" or "Band"
            certification TEXT NOT NULL,
            status VARCHAR NOT NULL,         -- Stores "Pending", "Approved", "Rejected"
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            processed_by VARCHAR,          -- The admin who processed the request (NULL if not yet processed)
            processed_at TIMESTAMP,          -- The timestamp of processing (NULL if not yet processed)
            
            -- Adding a composite index can speed up lookups for pending requests
            CONSTRAINT unique_pending_request UNIQUE (user_id, target_id, target_type, status)
        );
        """,
        List()
      )
      _ <- IO.println("Unified 'auth_request_table' created/updated.")
      
      // -- [REMOVED] The old, separate tables are no longer needed. --
      // The logic that created artist_auth_request_table and band_auth_request_table is now gone.

      // -- Preserved: Logic to create the initial administrator --
      _ <- IO.println("Checking and setting up initial administrator account...")
      encryptedPassword <- IO(CryptoUtils.encryptPassword(initialAdminPassword))

      // 1. Insert into user_table. ON CONFLICT ensures this is idempotent.
      _ <- writeDB(
        s"""
        INSERT INTO "${schemaName}"."user_table" (user_id, account, password)
        VALUES (?, ?, ?)
        ON CONFLICT (account) DO NOTHING;
        """,
        List(
          SqlParameter("String", initialAdminId),
          SqlParameter("String", initialAdminUsername),
          SqlParameter("String", encryptedPassword)
        )
      )

      // 2. Insert into admin_table. ON CONFLICT ensures this is idempotent.
      _ <- writeDB(
        s"""
        INSERT INTO "${schemaName}"."admin_table" (admin_id)
        VALUES (?)
        ON CONFLICT (admin_id) DO NOTHING;
        """,
        List(
          SqlParameter("String", initialAdminId)
        )
      )
      _ <- IO.println("Initial administrator account setup is complete.")

    } yield ()

    program.handleErrorWith(err => IO {
      println(s"[Error] Process.Init.init failed in project ${Global.ServiceCenter.projectName}. Please check db-manager connection and configuration.")
      err.printStackTrace()
    })
  }
}