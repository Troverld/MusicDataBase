package Impl

import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import cats.effect.IO
import cats.implicits._
import io.circe.Decoder
import io.circe.generic.auto._
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import Common.Serialize.CustomColumnTypes.decodeDateTime // 引入解码器

/**
 * Planner for validateUserMapping: 验证用户ID和用户令牌之间的映射关系是否有效.
 *
 * @param userID      需要验证的用户ID
 * @param userToken   需要验证的用户令牌
 * @param planContext 隐式执行上下文
 */
case class validateUserMappingPlanner(
                                       userID: String,
                                       userToken: String,
                                       override val planContext: PlanContext
                                     ) extends Planner[(Boolean, String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  // 定义一个私有 DTO 来安全地承载从数据库查询的令牌信息
  private case class UserTokenInfo(token: Option[String], tokenValidUntil: Option[DateTime])

  // 为 Joda DateTime 提供一个隐式解码器，以便 .as[T] 可以工作
  private implicit val dateTimeDecoder: Decoder[DateTime] = decodeDateTime

  override def plan(using planContext: PlanContext): IO[(Boolean, String)] = {
    // 核心验证逻辑，成功则返回 Unit，失败则抛出带有信息的异常
    val validationLogic: IO[Unit] = for {
      _ <- logInfo(s"开始验证用户 ${userID} 的令牌")

      // 步骤 1 & 2: 查找用户并获取其令牌信息
      storedInfo <- findUserTokenInfo(userID)

      // 步骤 3 & 4: 执行令牌匹配和有效期检查
      _ <- performValidationChecks(storedInfo)

    } yield ()

    // 将验证逻辑的结果映射到 API 定义的 (Boolean, String) 返回类型
    validationLogic.map { _ =>
      (true, "验证成功")
    }.handleErrorWith { error =>
      logError(s"用户 ${userID} 验证失败", error) >>
        IO.pure((false, error.getMessage))
    }
  }

  /**
   * 从数据库中查找用户的令牌和有效期信息.
   * 如果用户不存在，则返回一个携带错误信息的失败IO.
   */
  private def findUserTokenInfo(id: String)(using PlanContext): IO[UserTokenInfo] = {
    logInfo(s"正在数据库中查询用户 ${id} 的令牌信息")
    val sql = s"SELECT token, token_valid_until FROM ${schemaName}.user_table WHERE user_id = ?"
    readDBRows(sql, List(SqlParameter("String", id))).flatMap {
      case row :: Nil =>
        IO.fromEither(row.as[UserTokenInfo])
          .handleErrorWith(err => IO.raiseError(new Exception(s"解码UserTokenInfo失败: ${err.getMessage}")))
      case Nil =>
        IO.raiseError(new Exception("用户不存在"))
      case _ =>
        IO.raiseError(new Exception(s"数据库中存在多个相同的用户ID: ${id}"))
    }
  }

  /**
   * 执行令牌的匹配和有效期检查.
   */
  private def performValidationChecks(storedInfo: UserTokenInfo)(using PlanContext): IO[Unit] = {
    (storedInfo.token, storedInfo.tokenValidUntil) match {
      case (Some(storedToken), Some(validUntil)) =>
        val isTokenMatch = storedToken == userToken
        val isTokenExpired = DateTime.now().isAfter(validUntil)

        if (!isTokenMatch) {
          logInfo("提供的令牌与存储的令牌不匹配")
          IO.raiseError(new Exception("令牌无效或已在别处登录"))
        } else if (isTokenExpired) {
          logInfo(s"令牌已于 ${validUntil} 过期")
          IO.raiseError(new Exception("令牌已过期"))
        } else {
          logInfo("令牌匹配且在有效期内，验证通过")
          IO.unit
        }

      case _ =>
        logInfo("数据库中缺少用户的令牌或有效期信息")
        IO.raiseError(new Exception("用户令牌信息不完整"))
    }
  }

  private def logInfo(message: String): IO[Unit] = IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))
  private def logError(message: String, cause: Throwable): IO[Unit] = IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}
