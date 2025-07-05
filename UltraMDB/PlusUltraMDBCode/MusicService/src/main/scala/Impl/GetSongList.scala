package Impl

import APIs.OrganizeService.validateUserMapping
import Common.API.{PlanContext, Planner}
import Common.DBAPI.*
import Common.Object.SqlParameter
import Common.Serialize.CustomColumnTypes.{decodeDateTime, encodeDateTime}
import Common.ServiceUtils.schemaName
import Objects.CreatorService.{Artist, Band}
import Objects.MusicService.{Genre, Song}
import cats.effect.IO
import cats.implicits.*
import io.circe.*
import io.circe.generic.auto.*
import io.circe.parser.decode
import io.circe.syntax.*
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

case class GetSongList(
    userID: String,
    userToken: String,
    override val planContext: PlanContext
) extends Planner[(Option[List[Song]], String)]{
  val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  override def plan(using planContext: PlanContext): IO[(Option[List[Song]], String)] = {
    (
      for {
        (isValid,msg) <- validateUserMapping(userID, userToken).send
        _ <- if (!isValid)
          IO.raiseError(new Exception("User verification failed."))
        else IO.unit

        result <- performSearch

      } yield (Some(result),"")  // 成功：返回 true 和空错误信息
      ).handleErrorWith { e =>
      IO(logger.error(s"查询歌曲数据失败: ${e.getMessage}")) *>
        IO.pure((None, e.getMessage))  // 失败：返回 false 和错误信息
    }
  }


  private def performSearch(using PlanContext): IO[List[Song]] = {
    readDBRows(
      s"SELECT * FROM ${schemaName}.song_table",
      List.empty[SqlParameter]).flatMap { rows =>
      IO {
        logger.info(s"Query result count=${rows.length}")
        rows.map(json => decodeType[Song](json))
      }
    }
  }


}