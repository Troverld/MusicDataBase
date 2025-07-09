// ===== src/main/scala/Utils/SearchUtil.scala =====

package Utils

import Common.API.PlanContext
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import Objects.CreatorService.{Artist, Band}
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import io.circe.parser.parse
import org.slf4j.LoggerFactory

object SearchUtil {
  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  /**
   * 从数据库中根据 ID 获取单个艺术家的信息。
   *
   * @param artistID 要查询的艺术家 ID。
   * @param planContext 隐式执行上下文。
   * @return IO[Option[Artist]]，如果找到则为 Some(Artist)，否则为 None。
   */
  def fetchArtistFromDB(artistID: String)(using planContext: PlanContext): IO[Option[Artist]] = {
    logger.info(s"TID=${planContext.traceID.id} -- [SearchUtil] 正在数据库中查询艺术家: artist_id = ${artistID}")
    val query = s"""SELECT * FROM "${schemaName}"."artist_table" WHERE artist_id = ?"""
    val params = List(SqlParameter("String", artistID))

    readDBRows(query, params).flatMap {
      case row :: Nil =>
        IO.fromEither(row.as[Artist])
          .map(Some(_))
          .handleErrorWith { err =>
            logger.error(s"TID=${planContext.traceID.id} -- [SearchUtil] 解码Artist对象失败: ${err.getMessage}", err)
            IO.raiseError(new Exception(s"解码Artist对象失败: ${err.getMessage}"))
          }
      case Nil =>
        IO.pure(None)
      case _ =>
        logger.error(s"TID=${planContext.traceID.id} -- [SearchUtil] 数据库主键冲突: 存在多个ID为 ${artistID} 的艺术家")
        IO.raiseError(new Exception(s"数据库主键冲突: 存在多个ID为 ${artistID} 的艺术家"))
    }
  }

  /**
   * 从数据库中根据 ID 获取单个乐队的信息。
   *
   * 此方法包含了对 'members' 字段的修复逻辑，以处理双重编码的 JSON 字符串问题。
   *
   * @param bandID 要查询的乐队 ID。
   * @param planContext 隐式执行上下文。
   * @return IO[Option[Band]]，如果找到则为 Some(Band)，否则为 None。
   */
  def fetchBandFromDB(bandID: String)(using planContext: PlanContext): IO[Option[Band]] = {
    logger.info(s"TID=${planContext.traceID.id} -- [SearchUtil] 正在数据库中查询乐队: band_id = ${bandID}")
    val query = s"""SELECT * FROM "${schemaName}"."band_table" WHERE band_id = ?"""
    val params = List(SqlParameter("String", bandID))

    readDBRows(query, params).flatMap {
      case rawJson :: Nil =>
        val patchedJson = rawJson.mapObject { jsonObj =>
          jsonObj("members") match {
            case Some(jsonVal) if jsonVal.isString =>
              val jsonStr = jsonVal.asString.getOrElse("[]")
              parse(jsonStr) match {
                case Right(arrayJson) if arrayJson.isArray =>
                  jsonObj.add("members", arrayJson)
                case _ => jsonObj
              }
            case _ => jsonObj
          }
        }
        
        IO.fromEither(patchedJson.as[Band])
          .map(Some(_))
          .handleErrorWith { err =>
            logger.error(s"TID=${planContext.traceID.id} -- [SearchUtil] 对修复后的JSON解码Band对象失败: ${patchedJson.noSpaces}", err)
            IO.raiseError(new Exception(s"解码Band对象失败: ${err.getMessage}"))
          }

      case Nil =>
        IO.pure(None)

      case _ =>
        logger.error(s"TID=${planContext.traceID.id} -- [SearchUtil] 数据库主键冲突: 存在多个ID为 ${bandID} 的乐队")
        IO.raiseError(new Exception(s"数据库主键冲突: 存在多个ID为 ${bandID} 的乐队"))
    }
  }

  def fetchArtistsFromDB(artistIDs: List[String])(using planContext: PlanContext): IO[List[Artist]] = {
    if (artistIDs.isEmpty) return IO.pure(List.empty)
    logger.info(s"TID=${planContext.traceID.id} -- [SearchUtil] 正在数据库中批量查询艺术家: count=${artistIDs.length}")
    val placeholders = artistIDs.map(_ => "?").mkString(",")
    val query = s"""SELECT * FROM "${schemaName}"."artist_table" WHERE artist_id IN ($placeholders)"""
    val params = artistIDs.map(SqlParameter("String", _))

    readDBRows(query, params).flatMap { rows =>
      rows.traverse { row =>
        IO.fromEither(row.as[Artist])
      }.handleErrorWith { err =>
        logger.error(s"TID=${planContext.traceID.id} -- [SearchUtil] 批量解码Artist对象失败: ${err.getMessage}", err)
        IO.raiseError(new Exception(s"批量解码Artist对象失败: ${err.getMessage}"))
      }
    }
  }

  /**
   * [新增] 在 band_table 中查询 members 字段包含指定 artistID 的所有记录。
   *
   * @param artistID 艺术家的 ID。
   * @return 一个包含所有匹配的乐队 ID 的列表。
   */
  def findBandsByMemberFromDB(artistID: String)(using planContext: PlanContext): IO[List[String]] = {
    logger.info(s"TID=${planContext.traceID.id} -- [SearchUtil] 正在数据库中搜索包含成员 ${artistID} 的乐队...")

    // 使用 PostgreSQL 的 JSONB 操作符 @> 来检查数组是否包含某个元素
    val query = s"""SELECT band_id FROM "${schemaName}"."band_table" WHERE members::jsonb @> ?::jsonb"""
    val artistIdAsJsonArray = s"""["$artistID"]""" // 将 artistID 包装成 JSON 数组字符串
    val params = List(SqlParameter("String", artistIdAsJsonArray))

    readDBRows(query, params).flatMap { rows =>
      rows.traverse { row =>
        IO.fromEither(row.hcursor.get[String]("bandID"))
      }.handleErrorWith { error =>
        val errorMsg = s"从数据库行解码 bandID 失败: ${error.getMessage}"
        logger.error(s"TID=${planContext.traceID.id} -- [SearchUtil] $errorMsg", error)
        IO.raiseError(new Exception(errorMsg, error))
      }
    }
  }
}