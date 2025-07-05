package Impl

import Common.API.{PlanContext, Planner}
import Common.DBAPI._ // Assuming readDBRows is available
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import APIs.OrganizeService.validateUserMapping
import APIs.CreatorService.GetAllCreators // 1. 导入新的API
import APIs.StatisticsService.{GetCreatorCreationTendency, GetCreatorGenreStrength} // 1. 导入新的API
import Objects.CreatorService.{CreatorID_Type, CreatorType}
import Objects.StatisticsService.{Profile, Dim}
import Utils.StatisticsUtils
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory

/**
 * Planner for GetSimilarCreators: 获取与指定创作者相似的其他创作者 (重构版)
 *
 * @param userID      用户ID
 * @param userToken   用户认证令牌
 * @param creator     目标创作者的智能ID对象
 * @param limit       返回的相似创作者数量
 * @param planContext 执行上下文
 */
case class GetSimilarCreatorsPlanner(
                                      userID: String,
                                      userToken: String,
                                      creator: CreatorID_Type, // 2. 使用类型安全的 CreatorID_Type
                                      limit: Int,
                                      override val planContext: PlanContext
                                    ) extends Planner[(Option[List[CreatorID_Type]], String)] { // 3. 更新返回类型

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  // -- 辅助数据结构 --
  case class CreatorMetrics(creator: CreatorID_Type, tendency: Profile, strength: Profile)
  case class RankedCreator(creator: CreatorID_Type, score: Double)

  override def plan(using planContext: PlanContext): IO[(Option[List[CreatorID_Type]], String)] = {
    val logic: IO[List[CreatorID_Type]] = for {
      _ <- logInfo(s"开始查找与创作者 ${creator.id} (${creator.creatorType}) 相似的创作者，限制数量: ${limit}")
      _ <- validateUser()
      _ <- validateLimit()

      // 步骤 1: 并行获取目标创作者的统计数据和其他所有创作者的列表
      _ <- logInfo("正在并行获取目标数据和所有候选创作者列表...")
      initialData <- (
        fetchCreatorMetrics(creator), // 获取目标创作者的倾向和实力
        fetchAllOtherCreators()       // 获取所有其他创作者
      ).parTupled

      (targetMetrics, allOtherCreators) = initialData
      _ <- logInfo(s"目标数据获取成功。找到 ${allOtherCreators.length} 位其他创作者。")

      // 步骤 2: 并行获取所有候选创作者的统计数据
      _ <- logInfo("正在并行获取所有候选创作者的统计数据 (此操作可能耗时较长)...")
      candidateMetrics <- fetchAllCandidateMetrics(allOtherCreators)
      _ <- logInfo(s"成功获取了 ${candidateMetrics.length} 位候选创作者的数据。")

      // 步骤 3: 计算综合评分并排序
      _ <- logInfo("正在计算综合评分并排序...")
      rankedCreators = rankCreators(targetMetrics, candidateMetrics)

      // 步骤 4: 提取最终结果
      finalResult = rankedCreators.take(limit)
      _ <- logInfo(s"查找完成，返回前 ${finalResult.length} 位最相似的创作者。")

    } yield finalResult.map(_.creator)

    logic.map { creators =>
      (Some(creators), "相似创作者查找成功")
    }.handleErrorWith { error =>
      logError(s"查找创作者 ${creator.id} 的相似创作者失败", error) >>
        IO.pure((None, error.getMessage))
    }
  }

  // --- 验证逻辑 ---
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

  // --- 数据获取逻辑 ---

  /** 获取单个创作者的倾向和实力数据 */
  private def fetchCreatorMetrics(c: CreatorID_Type)(using PlanContext): IO[CreatorMetrics] = {
    (
      GetCreatorCreationTendency(userID, userToken, c).send,
      GetCreatorGenreStrength(userID, userToken, c).send
    ).parTupled.flatMap {
      case ((Some(tendency), _), (Some(strength), _)) =>
        IO.pure(CreatorMetrics(c, tendency, strength))
      case ((tendencyOpt, tendencyMsg), (strengthOpt, strengthMsg)) =>
        val errorDetails = s"Tendency: ${tendencyOpt.isDefined} ($tendencyMsg), Strength: ${strengthOpt.isDefined} ($strengthMsg)"
        IO.raiseError(new Exception(s"获取创作者 ${c.id} 的核心数据失败: $errorDetails"))
    }
  }

  /** 使用 GetAllCreators API 获取除目标外的所有创作者 */
  private def fetchAllOtherCreators()(using PlanContext): IO[List[CreatorID_Type]] = {
    GetAllCreators(userID, userToken).send.flatMap {
      case (Some(all), _) => IO.pure(all.filterNot(_.id == creator.id))
      case (None, msg)    => IO.raiseError(new Exception(s"无法获取所有创作者列表: $msg"))
    }
  }

  /** 并行获取所有候选创作者的统计数据，并优雅地处理失败 */
  private def fetchAllCandidateMetrics(candidates: List[CreatorID_Type])(using PlanContext): IO[List[CreatorMetrics]] = {
    candidates.parTraverse { c =>
      fetchCreatorMetrics(c).attempt.map {
        case Right(metrics) => Some(metrics)
        case Left(error) =>
          logger.warn(s"TID=${planContext.traceID.id} -- 获取候选者 ${c.id} 数据失败，将跳过此创作者: ${error.getMessage}")
          None
      }
    }.map(_.flatten) // 过滤掉所有失败的结果
  }

  // --- 核心排序逻辑 ---

  /**
   * 根据综合评分对候选创作者进行排序
   * @param target    目标创作者的统计数据
   * @param candidates 候选创作者的统计数据列表
   * @return 按综合评分降序排列的创作者列表
   */
  private def rankCreators(target: CreatorMetrics, candidates: List[CreatorMetrics]): List[RankedCreator] = {
    candidates.map { candidate =>
        // 计算余弦相似度
        val similarity = StatisticsUtils.calculateCosineSimilarity(target.tendency, candidate.tendency)

        // 计算对数实力因子
        val totalStrength = candidate.strength.vector.map(_.value).sum
        val strengthFactor = Math.log1p(totalStrength) // log(1 + strength)

        // 最终综合评分
        val finalScore = similarity * strengthFactor

        RankedCreator(candidate.creator, finalScore)
      }
      .filter(_.score > 0) // 过滤掉没有相似性或影响力的
      .sortBy(-_.score)    // 按分数降序排序
  }

  private def logInfo(message: String): IO[Unit] =
    IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))

  private def logError(message: String, cause: Throwable): IO[Unit] =
    IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}