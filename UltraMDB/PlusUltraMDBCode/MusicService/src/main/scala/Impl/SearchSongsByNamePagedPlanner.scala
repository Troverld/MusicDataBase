package Impl

import APIs.OrganizeService.validateUserMapping
import Common.API.{PlanContext, Planner}
import Common.DBAPI.*
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import cats.effect.IO
import cats.implicits.*
import org.slf4j.LoggerFactory
import io.circe.generic.auto.*

case class SearchSongsByNamePagedPlanner(
                                          userID: String,
                                          userToken: String,
                                          keywords: String,
                                          pageNumber: Int,
                                          pageSize: Int,
                                          override val planContext: PlanContext
                                        ) extends Planner[(Option[(List[String], Int)], String)] {

  private val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  override def plan(using planContext: PlanContext): IO[(Option[(List[String], Int)], String)] = {
    (
      for {
        // Step 1: 验证用户身份
        _ <- logInfo(s"验证用户 userID=$userID")
        (isValid, msg) <- validateUserMapping(userID, userToken).send
        _ <- if (!isValid) IO.raiseError(new IllegalAccessException("用户认证失败")) else IO.unit

        // Step 2: 验证输入合法性
        _ <- validateInputs()

        // Step 3: 执行查询
        (songs, totalPages) <- searchAndCountSongs()
      } yield (Some((songs, totalPages)), "查询成功")
      ).handleErrorWith { e =>
      logError("搜索失败", e) >> IO.pure((None, e.getMessage))
    }
  }

  private def validateInputs()(using PlanContext): IO[Unit] = {
    if (keywords.trim.isEmpty) IO.raiseError(new IllegalArgumentException("关键词不能为空"))
    else if (pageNumber <= 0) IO.raiseError(new IllegalArgumentException("页码必须 >= 1"))
    else if (pageSize <= 0) IO.raiseError(new IllegalArgumentException("每页大小必须 >= 1"))
    else IO.unit
  }

  private def searchAndCountSongs()(using PlanContext): IO[(List[String], Int)] = {
    val pattern = s"%$keywords%"
    val offset = (pageNumber - 1) * pageSize

    val dataSql =
      s"""
         |SELECT song_id
         |FROM ${schemaName}.song_table
         |WHERE name ILIKE ?
         |ORDER BY song_id
         |LIMIT ? OFFSET ?
       """.stripMargin

    val countSql =
      s"""
         |SELECT COUNT(*)
         |FROM ${schemaName}.song_table
         |WHERE name ILIKE ?
       """.stripMargin

    val dataParams = List(
      SqlParameter("String", pattern),
      SqlParameter("Int", pageSize.toString),
      SqlParameter("Int", offset.toString)
    )

    val countParams = List(SqlParameter("String", pattern))

    val songsIO = readDBRows(dataSql, dataParams).map(_.map(json => decodeField[String](json, "song_id")))
    val countIO = readDBInt(countSql, countParams)

    (songsIO, countIO).parTupled.map { case (songs, totalCount) =>
      val totalPages = (totalCount + pageSize - 1) / pageSize
      (songs, totalPages)
    }
  }

  private def logInfo(msg: String): IO[Unit] = IO(logger.info(s"TID=${planContext.traceID.id} -- $msg"))
  private def logError(msg: String, e: Throwable): IO[Unit] = IO(logger.error(s"TID=${planContext.traceID.id} -- $msg", e))
}
