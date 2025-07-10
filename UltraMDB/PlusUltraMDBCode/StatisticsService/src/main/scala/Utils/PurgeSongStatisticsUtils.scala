// ===== src/main/scala/Utils/PurgeSongStatisticsUtils.scala (NEW FILE) =====

package Utils

import Common.API.PlanContext
import cats.effect.IO
import cats.implicits._
import org.slf4j.LoggerFactory

object PurgeSongStatisticsUtils {
  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  /**
   * 核心业务逻辑：清理一首歌的所有统计数据。
   * 这包括删除所有相关的评分记录和播放记录。
   */
  def purgeStatistics(songID: String)(using planContext: PlanContext): IO[Unit] = {
    for {
      _ <- logInfo(s"在Utils层开始清理歌曲 ${songID} 的所有统计数据")
      
      // 步骤 1: 删除所有对该歌曲的评分
      _ <- logInfo(s"正在删除歌曲 ${songID} 的所有评分记录...")
      _ <- SearchUtils.deleteAllRatingsForSong(songID)
      
      // 步骤 2: 删除所有对该歌曲的播放记录
      _ <- logInfo(s"正在删除歌曲 ${songID} 的所有播放记录...")
      _ <- SearchUtils.deleteAllPlaybackLogsForSong(songID)
      
      _ <- logInfo(s"歌曲 ${songID} 的统计数据已全部从数据库中移除")
    } yield ()
  }
  
  private def logInfo(message: String)(using pc: PlanContext): IO[Unit] =
    IO(logger.info(s"TID=${pc.traceID.id} -- $message"))
}