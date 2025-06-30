package Impl


import APIs.OrganizeService.ValidateUserMapping
import Objects.TrackService.Collection
import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import cats.effect.IO
import org.slf4j.LoggerFactory
import org.joda.time.DateTime
import io.circe.syntax._
import io.circe.generic.auto._
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
import Objects.TrackService.Collection
import io.circe._
import cats.implicits.*
import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

case class CreateCollectionPlanner(
    name: String,
    description: String,
    contents: List[String],
    userID: String,
    userToken: String,
    override val planContext: PlanContext
) extends Planner[String] {
  val logger = LoggerFactory.getLogger(this.getClass.getSimpleName + "_" + planContext.traceID.id)

  override def plan(using PlanContext): IO[String] = {
    for {
      // Step 1: Validate user token and userID association
      isValid <- ValidateUserMappingAssociation()
      _ <- if (!isValid) {
        IO(logger.error("用户验证失败，userID和userToken关联无效")) >>
          IO.raiseError(new IllegalArgumentException("用户验证失败：userID和userToken关联无效"))
      } else IO(logger.info("用户验证通过"))

      // Step 2: Check if name is empty
      _ <- IO(logger.info("检查歌单名称是否为空"))
      _ <- if (name.isBlank) {
        IO(logger.error("歌单名称不能为空")) >>
          IO.raiseError(new IllegalArgumentException("歌单名称不能为空"))
      } else IO.unit

      // Step 3: Construct the initial collection data
      _ <- IO(logger.info("构建歌单的初始记录"))
      collectionID <- IO(java.util.UUID.randomUUID().toString)
      uploadTime <- IO(DateTime.now)
      _ <- IO(logger.info(s"生成的歌单ID=${collectionID}, 上传时间=${uploadTime}"))
      collectionRecord <- IO(
        Collection(
          collectionID = collectionID,
          name = name,
          ownerID = userID,
          maintainers = List(userID),
          uploadTime = uploadTime,
          description = description,
          contents = contents
        )
      )
      _ <- IO(logger.info(s"生成的歌单记录: ${collectionRecord}"))

      // Step 4: Insert the new record into the database
      _ <- IO(logger.info("插入数据库"))
      _ <- insertCollectionIntoDatabase(collectionRecord)

      // Step 5: Return the generated collectionID
      _ <- IO(logger.info("成功生成歌单，返回collectionID"))
    } yield collectionID
  }

  private def ValidateUserMappingAssociation()(using PlanContext): IO[Boolean] = {
    IO(logger.info("调用ValidateUserMapping验证用户令牌的合法性以及userID的身份关联有效性")) >>
      ValidateUserMapping(userID, userToken).send
  }

  private def insertCollectionIntoDatabase(collection: Collection)(using PlanContext): IO[Unit] = {
    val sql =
      s"""
         |INSERT INTO ${schemaName}.collection_table
         |(collection_id, name, owner_id, description, contents, maintainers, upload_time)
         |VALUES (?, ?, ?, ?, ?, ?, ?)
         |""".stripMargin
    val params = List(
      SqlParameter("String", collection.collectionID),
      SqlParameter("String", collection.name),
      SqlParameter("String", collection.ownerID),
      SqlParameter("String", collection.description),
      SqlParameter("Array[String]", collection.contents.asJson.noSpaces),
      SqlParameter("Array[String]", collection.maintainers.asJson.noSpaces),
      SqlParameter("DateTime", collection.uploadTime.getMillis.toString)
    )
    writeDB(sql, params).void
  }
}