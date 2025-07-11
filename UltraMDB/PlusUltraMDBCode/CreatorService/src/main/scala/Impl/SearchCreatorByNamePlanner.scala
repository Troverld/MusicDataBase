package Impl

import APIs.CreatorService.SearchCreatorByName
import APIs.OrganizeService.validateUserMapping
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import Objects.CreatorService.{CreatorID_Type, CreatorType} // 移除了 SearchCreatorResponse
import cats.effect.IO
import cats.implicits._
import io.circe.Json
import org.slf4j.LoggerFactory
import io.circe.generic.auto._ // Assuming companion objects are the standard

case class SearchCreatorByNamePlanner(
  userID: String,
  userToken: String,
  name: String,
  creatorType: Option[CreatorType],
  pageNumber: Int,
  pageSize: Int,
  override val planContext: PlanContext
) extends Planner[(Option[(List[CreatorID_Type], Int)], String)] { // <-- 修改点 1: 更新返回类型

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Option[(List[CreatorID_Type], Int)], String)] = {
    val logic: IO[(Option[(List[CreatorID_Type], Int)], String)] = for {
      _ <- validateUser()
      _ <- validateInputs()
      // 修改点 2: 直接解构元组结果
      (results, totalCount) <- searchAndCountCreators()
    } yield {
      // 修改点 3: 构建最终的元组返回结构
      val message = if (totalCount == 0) "未找到匹配的创作者" else "查询成功"
      (Some((results, totalCount)), message)
    }

    logic.handleErrorWith { error =>
      logError(s"搜索创作者 '${name}' 的操作失败", error) >>
        // 失败时的返回值现在也符合新的类型
        IO.pure((None, error.getMessage))
    }
  }

  // ... validateUser 和 validateInputs 方法保持不变 ...

  private def validateUser()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证用户身份: userID=${userID}")
    validateUserMapping(userID, userToken).send.flatMap {
      case (true, _) => logInfo("用户验证通过。")
      case (false, message) => IO.raiseError(new Exception(s"用户认证失败: $message"))
    }
  }

  private def validateInputs()(using PlanContext): IO[Unit] = {
    logInfo("正在验证输入参数...")
    if (name.trim.isEmpty) {
      IO.raiseError(new IllegalArgumentException("创作者搜索名称不能为空"))
    } else if (pageNumber <= 0) {
      IO.raiseError(new IllegalArgumentException("页码必须大于0"))
    } else if (pageSize <= 0) {
      IO.raiseError(new IllegalArgumentException("每页大小必须大于0"))
    } else {
      logInfo(s"输入参数验证通过: name='${name}', page=${pageNumber}, size=${pageSize}")
    }
  }
  
  /**
   * 核心逻辑：并行执行搜索和计数查询，然后将结果组合成一个元组。
   * 修改点 4: 方法的返回类型变为 IO[(List[CreatorID_Type], Int)]
   */
  private def searchAndCountCreators()(using PlanContext): IO[(List[CreatorID_Type], Int)] = {
    val (searchQuery, countQuery, params) = buildQueriesAndParams()

    val searchIO: IO[List[CreatorID_Type]] = executeSearch(searchQuery, params)
    val countIO: IO[Int] = executeCount(countQuery, params.filterNot(_.isInstanceOf[Int]))

    // 修改点 5: 使用 parTupled 将两个IO的结果合并为一个元组IO，更简洁
    (searchIO, countIO).parTupled
  }

  // ... buildQueriesAndParams, executeSearch, executeCount, parseCreatorID, 和 logging 方法保持不变 ...
  
  private def buildQueriesAndParams(): (String, String, List[SqlParameter]) = {
    val searchNameParam = s"%$name%"
    val artistQueryPart = s"""SELECT artist_id AS id, 'artist' AS creator_type FROM "${schemaName}"."artist_table" WHERE name ILIKE ?"""
    val bandQueryPart = s"""SELECT band_id AS id, 'band' AS creator_type FROM "${schemaName}"."band_table" WHERE name ILIKE ?"""

    val (unionQuery, params) = creatorType match {
      case Some(CreatorType.Artist) => (artistQueryPart, List(SqlParameter("String", searchNameParam)))
      case Some(CreatorType.Band)   => (bandQueryPart, List(SqlParameter("String", searchNameParam)))
      case None                     => (s"$artistQueryPart UNION ALL $bandQueryPart", List(SqlParameter("String", searchNameParam), SqlParameter("String", searchNameParam)))
    }

    val offset = (pageNumber - 1) * pageSize
    val pagedQuery = s"$unionQuery LIMIT ? OFFSET ?"
    val pagedParams = params ++ List(SqlParameter("Int", pageSize.toString), SqlParameter("Int", offset.toString))
    
    val countQuery = s"SELECT COUNT(*) FROM ($unionQuery) AS combined_results"
    
    (pagedQuery, countQuery, pagedParams)
  }

  private def executeSearch(query: String, params: List[SqlParameter])(using PlanContext): IO[List[CreatorID_Type]] = {
    logInfo(s"执行搜索查询: $query")
    readDBRows(query, params).flatMap { rows =>
      rows.traverse { row =>
        IO.fromEither(parseCreatorID(row))
      }
    }
  }

  private def executeCount(query: String, params: List[SqlParameter])(using PlanContext): IO[Int] = {
    logInfo(s"执行计数查询: $query")
    readDBInt(query, params)
  }

  private def parseCreatorID(row: Json): Either[io.circe.Error, CreatorID_Type] = {
    val cursor = row.hcursor
    for {
      id        <- cursor.get[String]("id")
      typeStr   <- cursor.get[String]("creator_type")
      creatorID <- CreatorID_Type.apply(typeStr, id).toEither.left.map(e => io.circe.DecodingFailure(e.getMessage, cursor.history))
    } yield creatorID
  }

  private def logInfo(message: String): IO[Unit] = IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))
  private def logError(message: String, cause: Throwable): IO[Unit] = IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}