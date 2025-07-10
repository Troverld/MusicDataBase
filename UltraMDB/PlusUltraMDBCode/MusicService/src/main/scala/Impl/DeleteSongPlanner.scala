package Impl


import APIs.OrganizeService.validateAdminMapping
import Common.API.{PlanContext, Planner}
import Common.DBAPI.*
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import cats.effect.IO
import org.slf4j.LoggerFactory
import io.circe.syntax.*
import io.circe.generic.auto.*
import org.joda.time.DateTime
import cats.implicits.*
import Common.Serialize.CustomColumnTypes.{decodeDateTime, encodeDateTime}
import io.circe.*
import io.circe.syntax.*
import io.circe.generic.auto.*
import org.joda.time.DateTime
import cats.implicits.*
import Common.DBAPI.*
import Common.API.{PlanContext, Planner}
import cats.effect.IO
import Common.Object.SqlParameter
import Common.Serialize.CustomColumnTypes.{decodeDateTime, encodeDateTime}
import Common.ServiceUtils.schemaName
import APIs.OrganizeService.validateAdminMapping
import APIs.StatisticsService.PurgeSongStatisticsMessage
import io.circe.*
import Common.Serialize.CustomColumnTypes.{decodeDateTime, encodeDateTime}

case class DeleteSongPlanner(
                              adminID: String,
                              adminToken: String,
                              songID: String,
                              override val planContext: PlanContext
                            ) extends Planner[(Boolean, String)] {

  val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  override def plan(using planContext: PlanContext): IO[(Boolean, String)] = {
    (
      for {
        // Step 1: 验证管理员身份
        _ <- IO(logger.info(s"验证管理员身份，adminID: ${adminID}, adminToken: ${adminToken}"))
        (isAdminValid, msg) <- validateAdminMapping(adminID, adminToken).send
        _ <- IO(logger.info(s"管理员验证结果: ${isAdminValid}"))
        _ <- if (!isAdminValid) IO.raiseError(new Exception("管理员认证失败")) else IO.unit

        // Step 2: 检查 songID 是否存在
        _ <- IO(logger.info(s"检查歌曲是否存在，songID: ${songID}"))
        songExists <- checkSongExists(songID)
        _ <- if (!songExists) IO.raiseError(new Exception("歌曲不存在")) else IO.unit

        // Step 3: 检查引用情况
//        _ <- IO(logger.info(s"检查歌曲是否被引用, songID: ${songID}"))
//        isReferenced <- checkSongReferenced(songID)
//        _ <- if (isReferenced) IO.raiseError(new Exception("歌曲被引用，无法删除")) else IO.unit

        // Step 4: 删除歌曲记录
        _ <- IO(logger.info(s"删除songID歌曲播放记录: ${songID}"))
        (del, msg2) <- PurgeSongStatisticsMessage(adminID,adminToken,songID).send
        _ <- IO(logger.info(s"从SongTable删除歌曲记录, songID: ${songID}"))
        deleteResult <- deleteSong(songID)
        _ <- IO(logger.info(s"删除结果: ${deleteResult}"))
      } yield (true, "") // 成功时返回 true 和空字符串
      ).handleErrorWith { e =>
      IO(logger.error("删除歌曲过程中出错: " + e.getMessage)) *>
        IO.pure((false, e.getMessage)) // 失败时返回 false 和错误信息
    }
  }

  // 检查歌曲是否存在
  private def checkSongExists(songID: String)(using PlanContext): IO[Boolean] = {
    val sql =
      s"""
SELECT COUNT(1)
FROM ${schemaName}.song_table
WHERE song_id = ?;
""".stripMargin
    readDBInt(sql, List(SqlParameter("String", songID))).map(_ > 0)
  }

  // 检查歌曲是否被引用
  private def checkSongReferenced(songID: String)(using PlanContext): IO[Boolean] = {
    for {
      // 检查专辑(Album)是否引用歌曲
      _ <- IO(logger.info(s"检查专辑引用，songID: ${songID}"))
      albumReferenceCount <- readDBInt(
        s"SELECT COUNT(1) FROM ${schemaName}.album WHERE song_ids @> ?;",
        List(SqlParameter("Array[String]", List(songID).asJson.noSpaces))
      )

      // 检查收藏(收藏夹/Collection)是否引用歌曲
      _ <- IO(logger.info(s"检查歌单引用，songID: ${songID}"))
      collectionReferenceCount <- readDBInt(
        s"SELECT COUNT(1) FROM ${schemaName}.collection WHERE song_ids @> ?;",
        List(SqlParameter("Array[String]", List(songID).asJson.noSpaces))
      )

      // 检查播放列表(Playlist)是否引用歌曲
      _ <- IO(logger.info(s"检查播放集引用，songID: ${songID}"))
      playlistReferenceCount <- readDBInt(
        s"SELECT COUNT(1) FROM ${schemaName}.playlist WHERE song_ids @> ?;",
        List(SqlParameter("Array[String]", List(songID).asJson.noSpaces))
      )
      _ <- IO(logger.info(s"引用统计: 专辑=${albumReferenceCount}, 歌单=${collectionReferenceCount}, 播放列表=${playlistReferenceCount}"))
    } yield albumReferenceCount > 0 || collectionReferenceCount > 0 || playlistReferenceCount > 0
  }

  // 删除歌曲
  private def deleteSong(songID: String)(using PlanContext): IO[String] = {
    val sql =
      s"""
DELETE FROM ${schemaName}.song_table
WHERE song_id = ?;
""".stripMargin
    writeDB(sql, List(SqlParameter("String", songID)))
  }
}