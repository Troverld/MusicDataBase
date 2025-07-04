package Impl

import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import APIs.OrganizeService.validateUserMapping
import Objects.StatisticsService.Profile
import Utils.StatisticsUtils
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory

/**
 * Planner for GetUserPortrait: 获取用户的音乐偏好画像
 *
 * @param userID      目标用户的ID
 * @param userToken   用户认证令牌
 * @param planContext 执行上下文
 */
case class GetUserPortraitPlanner(
                                   userID: String,
                                   userToken: String,
                                   override val planContext: PlanContext
                                 ) extends Planner[(Option[Profile], String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Option[Profile], String)] = {
    val logic: IO[Profile] = for {
      _ <- logInfo(s"开始获取用户 ${userID} 的音乐偏好画像")

      // 步骤1: 验证用户身份
      _ <- validateUser()

      // 步骤2: 尝试从缓存获取用户画像
      cachedProfile <- tryGetFromCache()

      // 步骤3: 如果缓存不存在或过期，实时计算用户画像
      profile <- cachedProfile match {
        case Some(profile) =>
          logInfo("从缓存获取用户画像成功") >> IO.pure(profile)
        case None =>
          logInfo("缓存不存在或已过期，开始实时计算用户画像") >>
          calculateAndCacheProfile()
      }

    } yield profile

    logic.map { profile =>
      (Some(profile), "获取用户画像成功")
    }.handleErrorWith { error =>
      logError(s"获取用户 ${userID} 画像失败", error) >>
        IO.pure((None, error.getMessage))
    }
  }

  /**
   * 步骤1: 验证用户身份
   */
  private def validateUser()(using PlanContext): IO[Unit] = {
    logInfo("正在验证用户身份") >> {
      validateUserMapping(userID, userToken).send.flatMap { case (isValid, message) =>
        if (isValid) {
          logInfo("用户身份验证通过")
        } else {
          IO.raiseError(new IllegalArgumentException(s"用户身份验证失败: $message"))
        }
      }
    }
  }

  /**
   * 步骤2: 尝试从缓存获取用户画像
   */
  private def tryGetFromCache()(using PlanContext): IO[Option[Profile]] = {
    for {
      _ <- logInfo("检查用户画像缓存")
      isExpired <- StatisticsUtils.isCacheExpired(userID)
      profile <- if (isExpired) {
        logInfo("缓存已过期或不存在")
        IO.pure(None)
      } else {
        logInfo("缓存有效，尝试获取")
        StatisticsUtils.getUserPortraitFromCache(userID)
      }
    } yield profile
  }

  /**
   * 步骤3: 实时计算用户画像并更新缓存
   */
  private def calculateAndCacheProfile()(using PlanContext): IO[Profile] = {
    for {
      _ <- logInfo("开始实时计算用户画像")
      
      // 检查用户是否有播放记录
      hasData <- checkUserHasData()
      
      profile <- if (hasData) {
        // 有数据，计算用户画像
        for {
          portraitVector <- StatisticsUtils.calculateUserPortrait(userID)
          profile = Profile(portraitVector, norm = true)
          _ <- logInfo(s"计算出用户画像，包含 ${portraitVector.length} 个曲风偏好")
          
          // 更新缓存
          _ <- StatisticsUtils.updateUserPortraitCache(userID)
          _ <- logInfo("用户画像缓存已更新")
        } yield profile
      } else {
        // 没有数据，返回空画像
        logInfo("用户暂无播放记录，返回空画像") >>
        IO.pure(Profile(List.empty, norm = true))
      }
    } yield profile
  }

  /**
   * 检查用户是否有播放或评分数据
   */
  private def checkUserHasData()(using PlanContext): IO[Boolean] = {
    for {
      playbackCount <- getPlaybackCount()
      ratingCount <- getRatingCount()
      hasData = playbackCount > 0 || ratingCount > 0
      _ <- logInfo(s"用户数据统计: 播放记录 ${playbackCount} 条，评分记录 ${ratingCount} 条")
    } yield hasData
  }

  /**
   * 获取用户播放记录数量
   */
  private def getPlaybackCount()(using PlanContext): IO[Int] = {
    val sql = s"SELECT COUNT(*) FROM ${schemaName}.playback_log WHERE user_id = ?"
    readDBInt(sql, List(SqlParameter("String", userID)))
  }

  /**
   * 获取用户评分记录数量
   */
  private def getRatingCount()(using PlanContext): IO[Int] = {
    val sql = s"SELECT COUNT(*) FROM ${schemaName}.song_rating WHERE user_id = ?"
    readDBInt(sql, List(SqlParameter("String", userID)))
  }

  private def logInfo(message: String): IO[Unit] = 
    IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))
    
  private def logError(message: String, cause: Throwable): IO[Unit] = 
    IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}