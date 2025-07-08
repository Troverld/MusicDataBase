package Impl

import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import APIs.OrganizeService.validateUserMapping
import APIs.MusicService.GetSongByID // 导入用于验证歌曲的API
import Utils.SearchUtils.fetchAverageRating // 导入用于查询平均评分的工具函数
import cats.effect.IO
import cats.implicits._
import org.slf4j.LoggerFactory
import io.circe.Json
import io.circe.generic.auto._

/**
 * Planner for GetAverageRating: 查询一首歌的平均评分和评分数量。
 *
 * @param userID    发起请求的用户ID
 * @param userToken 用户认证令牌
 * @param songID    被查询评分的歌曲ID
 * @param planContext 执行上下文
 */
case class GetAverageRatingPlanner(
                                    userID: String,
                                    userToken: String,
                                    songID: String,
                                    override val planContext: PlanContext
                                  ) extends Planner[((Double, Int), String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[((Double, Int), String)] = {
    val logic: IO[(Double, Int)] = for {
      _ <- logInfo(s"开始查询歌曲 ${songID} 的平均评分，由用户 ${userID} 发起")

      // 步骤 1: 验证API调用者的身份
      _ <- validateUser()

      // 步骤 2: 验证歌曲是否存在
      _ <- validateSong()

      // 步骤 3: 从数据库中获取平均评分和数量
      result <- fetchAverageRatingFromDB()

    } yield result

    logic.map { case (avg, count) =>
      ((avg, count), "查询成功")
    }.handleErrorWith { error =>
      logError(s"查询歌曲 ${songID} 的平均分失败", error) >>
        // 对于系统性错误，返回(-1.0, -1)以区别于“未评分”的(0.0, 0)
        IO.pure(((-1.0, -1), error.getMessage))
    }
  }

  /**
   * 验证发起请求的用户身份是否有效。
   */
  private def validateUser()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证调用者 ${userID} 的身份") >>
      validateUserMapping(userID, userToken).send.flatMap {
        case (true, _) => logInfo("调用者身份验证通过")
        case (false, message) => IO.raiseError(new IllegalArgumentException(s"调用者身份验证失败: $message"))
      }
  }

  /**
   * 验证目标歌曲是否存在。
   */
  private def validateSong()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证歌曲 ${songID} 是否存在") >>
      GetSongByID(userID, userToken, songID).send.flatMap {
        case (Some(_), _) => logInfo("歌曲存在性验证通过")
        case (None, message) => IO.raiseError(new IllegalArgumentException(s"歌曲不存在: $message"))
      }
  }

  /**
   * 从 song_rating 表中查询平均评分和评分总数。
   */
  private def fetchAverageRatingFromDB()(using PlanContext): IO[(Double, Int)] = {
    logInfo(s"正在调用 SearchUtils 查询歌曲 ${songID} 的评分数据")
    fetchAverageRating(songID)
  }

  private def logInfo(message: String): IO[Unit] =
    IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))

  private def logError(message: String, cause: Throwable): IO[Unit] =
    IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}