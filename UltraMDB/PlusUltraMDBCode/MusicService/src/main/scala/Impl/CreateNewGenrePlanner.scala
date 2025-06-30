package Impl



import APIs.OrganizeService.validateAdminMapping
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import cats.effect.IO
import org.slf4j.LoggerFactory
import io.circe.Json
import io.circe._
import io.circe.syntax._
import io.circe.generic.auto._
import org.joda.time.DateTime
import cats.implicits.*
import Common.Serialize.CustomColumnTypes.{decodeDateTime, encodeDateTime}
import io.circe._
import io.circe.syntax._
import io.circe.generic.auto._
import org.joda.time.DateTime
import cats.implicits.*
import Common.DBAPI._
import Common.API.{PlanContext, Planner}
import cats.effect.IO
import Common.Object.SqlParameter
import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}
import Common.ServiceUtils.schemaName
import APIs.OrganizeService.validateAdminMapping
import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

case class CreateNewGenrePlanner(
  adminID: String,
  adminToken: String,
  name: String,
  description: String,
  override val planContext: PlanContext
) extends Planner[(Option[String], String)] {

  // Logger for debugging and error tracking
  val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  override def plan(using planContext: PlanContext): IO[(Option[String], String)] = {
    (
      for {
        // Step 1: 验证管理员权限
        _ <- IO(logger.info(s"验证管理员权限: 管理员ID=${adminID}, 管理员Token=${adminToken}"))
        _ <- validateAdmin()

        // Step 2.1: 检查曲风名称是否为空
        _ <- IO(logger.info(s"检查曲风名称 ${name} 是否为空"))
        _ <- checkNameNotEmpty()

        // Step 2.2: 检查曲风名称是否已存在
        _ <- IO(logger.info(s"检查曲风名称 ${name} 是否已存在"))
        _ <- checkGenreNameUnique()

        // Step 3: 生成新的曲风ID并插入记录
        _ <- IO(logger.info(s"生成新的曲风ID"))
        genreID <- generateAndInsertGenre()

        _ <- IO(logger.info(s"新曲风创建成功，genreID=${genreID}"))
      } yield (Some(genreID), "") // 成功时返回 Some(id) 和空错误信息
      ).handleErrorWith { e =>
      IO(logger.error(s"创建曲风失败: ${e.getMessage}")) *>
        IO.pure((None, e.getMessage)) // 失败时返回 None 和错误信息
    }
  }


  /**
   * 验证管理员权限
   * Step 1.1: 调用validateAdminMapping以验证权限
   * @return IO[Unit]
   */
  private def validateAdmin()(using PlanContext): IO[Unit] = {
    validateAdminMapping(adminID, adminToken).send.flatMap { (isValid,msg) =>
      if (isValid) IO(logger.info("管理员权限验证通过"))
      else IO.raiseError(new IllegalStateException("管理员认证失败"))
    }
  }

  /**
   * 检查曲风名称是否为空
   * Step 2.1: 若为空抛出异常
   * @return IO[Unit]
   */
  private def checkNameNotEmpty()(using PlanContext): IO[Unit] = {
    if (name.trim.isEmpty) {
      IO.raiseError(new IllegalArgumentException("名称不能为空"))
    } else {
      IO(logger.info(s"曲风名称 ${name} 不为空"))
    }
  }

  /**
   * 检查曲风名称是否已存在
   * Step 2.2: 在GenreTable中查询是否存在冲突
   * @return IO[Unit]
   */
  private def checkGenreNameUnique()(using PlanContext): IO[Unit] = {
    val sql = s"SELECT COUNT(*) FROM ${schemaName}.genre_table WHERE name = ?"
    readDBInt(sql, List(SqlParameter("String", name))).flatMap { count =>
      if (count > 0) {
        IO.raiseError(new IllegalArgumentException("曲风名称已存在"))
      } else {
        IO(logger.info(s"曲风名称 ${name} 没有重复"))
      }
    }
  }

  /**
   * 生成新的曲风ID并插入数据库中
   * Step 3: 生成唯一genreID并插入GenreTable
   * @return IO[String] -> 生成的genreID
   */
  private def generateAndInsertGenre()(using PlanContext): IO[String] = {
    val newGenreID = java.util.UUID.randomUUID().toString
    val sql =
      s"""
         |INSERT INTO ${schemaName}.genre_table (genre_id, name, description)
         |VALUES (?, ?, ?)
       """.stripMargin
    writeDB(sql, List(
      SqlParameter("String", newGenreID),
      SqlParameter("String", name),
      SqlParameter("String", description)
    )).flatMap { _ =>
      IO(newGenreID)
    }
  }
}