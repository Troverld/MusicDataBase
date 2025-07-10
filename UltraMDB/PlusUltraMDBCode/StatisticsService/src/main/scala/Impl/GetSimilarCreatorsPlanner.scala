// ===== src/main/scala/Impl/GetSimilarCreatorsPlanner.scala =====

package Impl

import Common.API.{PlanContext, Planner}
import APIs.OrganizeService.validateUserMapping
import Objects.CreatorService.CreatorID_Type
import Utils.GetSimilarCreatorsUtils
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory

case class GetSimilarCreatorsPlanner(
                                      userID: String,
                                      userToken: String,
                                      createrID: CreatorID_Type,
                                      limit: Int,
                                      override val planContext: PlanContext
                                    ) extends Planner[(Option[List[CreatorID_Type]], String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Option[List[CreatorID_Type]], String)] = {
    val logic: IO[List[CreatorID_Type]] = for {
      _ <- logInfo(s"开始查找与创作者 ${createrID.id} (${createrID.creatorType}) 相似的创作者，限制数量: ${limit}")
      _ <- validateUser()
      _ <- validateLimit()
      _ <- logInfo("验证通过，正在调用 GetSimilarCreatorsUtils 执行查找逻辑")
      similarCreators <- GetSimilarCreatorsUtils.findSimilarCreators(userID, userToken, createrID, limit)
    } yield similarCreators

    logic.map { creators =>
      (Some(creators), "相似创作者查找成功")
    }.handleErrorWith { error =>
      logError(s"查找创作者 ${createrID.id} 的相似创作者失败", error) >>
        IO.pure((None, error.getMessage))
    }
  }

  private def validateUser()(using PlanContext): IO[Unit] = {
    logInfo("正在验证用户身份") >> validateUserMapping(userID, userToken).send.flatMap {
      case (true, _) => logInfo("用户身份验证通过")
      case (false, msg) => IO.raiseError(new IllegalArgumentException(s"用户身份验证失败: $msg"))
    }
  }

  private def validateLimit()(using PlanContext): IO[Unit] = {
    logInfo(s"验证参数: limit=${limit}") >> {
      if (limit <= 0 || limit > 50) {
        IO.raiseError(new IllegalArgumentException("相似创作者数量限制必须在1-50之间"))
      } else IO.unit
    }
  }

  private def logInfo(message: String): IO[Unit] =
    IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))

  private def logError(message: String, cause: Throwable): IO[Unit] =
    IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}