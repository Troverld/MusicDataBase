// ===== src\main\scala\Utils\GetSimilarCreatorsUtils.scala ===== 

package Utils

import Common.API.PlanContext
import APIs.CreatorService.GetAllCreators
import Objects.CreatorService.CreatorID_Type
import Objects.StatisticsService.Profile
import cats.effect.IO
import cats.implicits._
import org.slf4j.LoggerFactory
import io.circe.generic.auto._

object GetSimilarCreatorsUtils {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  private case class CreatorMetrics(creatorID: CreatorID_Type, tendency: Profile, strength: Profile)
  private case class RankedCreator(creatorID: CreatorID_Type, score: Double)

  def findSimilarCreators(
                           userID: String,
                           userToken: String,
                           creatorID: CreatorID_Type,
                           limit: Int
                         )(using planContext: PlanContext): IO[List[CreatorID_Type]] = {
    for {
      _ <- logInfo("正在获取目标数据和所有候选创作者列表...")
      targetMetrics <- fetchCreatorMetrics(userID, userToken, creatorID)
      allOtherCreators <- fetchAllOtherCreators(userID, userToken, creatorID)
      _ <- logInfo(s"目标数据获取成功。找到 ${allOtherCreators.length} 位其他创作者。")

      _ <- logInfo("正在串行获取所有候选创作者的统计数据...") // [COMMENT] Changed log message
      candidateMetrics <- fetchAllCandidateMetrics(userID, userToken, allOtherCreators)
      _ <- logInfo(s"成功获取了 ${candidateMetrics.length} 位候选创作者的数据。")

      _ <- logInfo("正在计算综合评分并排序...")
      rankedCreators = rankCreators(targetMetrics, candidateMetrics)

      finalResult = rankedCreators.take(limit)
      _ <- logInfo(s"查找完成，返回前 ${finalResult.length} 位最相似的创作者。")
    } yield finalResult.map(_.creatorID)
  }

  private def fetchCreatorMetrics(userID: String, userToken: String, c: CreatorID_Type)(using PlanContext): IO[CreatorMetrics] = {
    // [REFACTORED] Removed parTupled for sequential execution.
    for {
      _ <- logInfo(s"正在获取创作者 ${c.id} 的倾向和实力画像...")
      tendency <- GetCreatorCreationTendencyUtils.generateTendencyProfile(c, userID, userToken)
      strength <- GetCreatorGenreStrengthUtils.generateStrengthProfile(c, userID, userToken)
    } yield CreatorMetrics(c, tendency, strength)
  }.handleErrorWith { error =>
    IO.raiseError(new Exception(s"获取创作者 ${c.id} 的核心数据失败: ${error.getMessage}", error))
  }

  private def fetchAllOtherCreators(
                                     userID: String,
                                     userToken: String,
                                     targetCreator: CreatorID_Type
                                   )(using PlanContext): IO[List[CreatorID_Type]] =
    GetAllCreators(userID, userToken).send.flatMap {
      case (Some(all), _) => IO.pure(all.filterNot(_.id == targetCreator.id))
      case (None, msg) => IO.raiseError(new Exception(s"无法获取所有创作者列表: $msg"))
    }

  private def fetchAllCandidateMetrics(userID: String, userToken: String, candidates: List[CreatorID_Type])(using planContext: PlanContext): IO[List[CreatorMetrics]] =
    candidates.traverse { c =>
      fetchCreatorMetrics(userID, userToken, c).attempt.map {
        case Right(metrics) => Some(metrics)
        case Left(error) =>
          logger.warn(s"TID=${planContext.traceID.id} -- 获取候选者 ${c.id} 数据失败，将跳过此创作者: ${error.getMessage}")
          None
      }
    }.map(_.flatten)

  private def rankCreators(target: CreatorMetrics, candidates: List[CreatorMetrics]): List[RankedCreator] =
    candidates.map { candidate =>
      val similarity = StatisticsUtils.calculateCosineSimilarity(target.tendency, candidate.tendency)
      val totalStrength = candidate.strength.vector.map(_.value).sum
      val strengthFactor = Math.log1p(totalStrength)
      val finalScore = similarity * strengthFactor
      RankedCreator(candidate.creatorID, finalScore)
    }.filter(_.score > 0).sortBy(-_.score)

  private def logInfo(message: String)(using pc: PlanContext): IO[Unit] = IO(logger.info(s"TID=${pc.traceID.id} -- $message"))
}
// ===== End of src\main\scala\Utils\GetSimilarCreatorsUtils.scala ===== 