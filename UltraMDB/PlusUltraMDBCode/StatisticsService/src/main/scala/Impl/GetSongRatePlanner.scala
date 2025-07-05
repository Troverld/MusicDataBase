package Impl

import Common.API.{PlanContext, Planner}
import Common.DBAPI._ // 导入 writeDB 和我们假设的 readDBRows
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import APIs.OrganizeService.validateUserMapping
import APIs.StatisticsService.GetSongRate
import cats.effect.IO
import cats.implicits._
import org.slf4j.LoggerFactory
import io.circe.Json

import io.circe.generic.auto._

/**
 * Planner for GetSongRate: 查询指定用户对某首歌曲的评分。
 *
 * @param userID       发起请求的用户ID
 * @param userToken    发起请求的用户令牌
 * @param targetUserID 被查询评分的目标用户ID
 * @param songID       被查询评分的歌曲ID
 * @param planContext  执行上下文
 */
case class GetSongRatePlanner(
                               userID: String,
                               userToken: String,
                               targetUserID: String,
                               songID: String,
                               override val planContext: PlanContext
                             ) extends Planner[(Int, String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Int, String)] = {
    val logic: IO[Int] = for {
      _ <- logInfo(s"开始查询用户 ${targetUserID} 对歌曲 ${songID} 的评分，请求由用户 ${userID} 发起")

      // 步骤1: 验证API调用者的身份
      _ <- validateUser()

      // 步骤2: 从数据库中获取评分
      rating <- fetchRatingFromDB()

    } yield rating

    logic.map { rating =>
      (rating, if (rating > 0) "查询评分成功" else "用户未对该歌曲评分")
    }.handleErrorWith { error =>
      logError(s"查询评分失败", error) >>
        IO.pure((-1, error.getMessage))
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
   * 从 song_rating 表中查询评分。
   * 如果找到记录，返回评分 (1-5)。
   * 如果未找到记录，返回 0。
   */
  private def fetchRatingFromDB()(using PlanContext): IO[Int] = {
    logInfo(s"正在数据库中查询 user_id=${targetUserID} 和 song_id=${songID} 的评分")
    val sql = s"SELECT rating FROM ${schemaName}.song_rating WHERE user_id = ? AND song_id = ?"
    val params = List(SqlParameter("String", targetUserID), SqlParameter("String", songID))

    // **修正点**: 遵循项目实践，使用 readDBRows 辅助函数
    // 这个函数返回 IO[List[Json]]，非常适合处理可能返回0行或1行结果的查询
    readDBRows(sql, params)
      .map(_.headOption) // 安全地获取第一个结果（如果有的话），返回 IO[Option[Json]]
      .flatMap {
        case Some(json) =>
          val rating = decodeField[Int](json, "rating")
          logInfo(s"查询成功，找到评分为: $rating") >> IO.pure(rating)

        case None =>
          logInfo("未找到评分记录，按规定返回 0") >> IO.pure(0)
      }
  }

  private def logInfo(message: String): IO[Unit] =
    IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))

  private def logError(message: String, cause: Throwable): IO[Unit] =
    IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}