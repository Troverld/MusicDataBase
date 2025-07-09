package Utils

import Common.API.PlanContext
import APIs.MusicService.{FilterSongsByEntity, GetSongProfile}
import APIs.StatisticsService.GetSongPopularity
import Objects.CreatorService.CreatorID_Type
import Objects.StatisticsService.{Dim, Profile}
import cats.effect.IO
import cats.implicits._
import org.slf4j.LoggerFactory
import io.circe.generic.auto._

object GetCreatorGenreStrengthUtils {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  /**
   * 生成创作者的曲风实力画像。
   * 封装了获取作品、并行分析每首作品的属性、并聚合计算实力的全过程。
   */
  def generateStrengthProfile(creator: CreatorID_Type, userID: String, userToken: String)(using planContext: PlanContext): IO[Profile] = {
    for {
      _ <- logInfo(s"在Utils层开始计算创作者 ${creator.id} 的曲风实力")

      // **修正点**: 将 planContext 显式传递给辅助方法
      songs <- getCreatorSongs(creator, userID, userToken)(using planContext)
      _ <- logInfo(s"获取到创作者作品 ${songs.length} 首")

      strengthProfile <- if (songs.isEmpty) {
        logInfo("创作者暂无作品，返回空实力")(using planContext) >> // logInfo 也需要上下文
          IO.pure(Profile(List.empty, norm = false))
      } else {
        for {
          // **修正点**: 将 planContext 显式传递给辅助方法
          genreStrengthDims <- calculateGenreStrengths(songs, userID, userToken)(using planContext)
          _ <- logInfo(s"计算出曲风实力: ${genreStrengthDims}")(using planContext)
        } yield Profile(genreStrengthDims, norm = false)
      }
    } yield strengthProfile
  }

  /**
   * 内部辅助方法：获取创作者的所有作品ID列表。
   */
  private def getCreatorSongs(creator: CreatorID_Type, userID: String, userToken: String)(using planContext: PlanContext): IO[List[String]] = {
    FilterSongsByEntity(userID, userToken, Some(creator)).send.flatMap {
      case (Some(songs), _) => IO.pure(songs)
      case (None, message) =>
        logInfo(s"获取创作者作品失败: $message. 将视为空列表处理。")(using planContext) >> IO.pure(List.empty)
    }
  }

  /**
   * 内部辅助方法：计算各曲风的实力分数。
   */
  private def calculateGenreStrengths(songs: List[String], userID: String, userToken: String)(using planContext: PlanContext): IO[List[Dim]] = {
    for {
      // **修正点**: 将 planContext 显式传递给辅助方法
      songData <- songs.traverse(fetchSongData(_, userID, userToken)(using planContext))
      genreStrengths = calculateAveragePopularityByGenre(songData)
    } yield genreStrengths
  }

  /**
   * 内部辅助方法：并行获取单首歌曲的曲风和热度。
   */
  private def fetchSongData(songId: String, userID: String, userToken: String)(using planContext: PlanContext): IO[(List[String], Double)] = {
    // 【串行】
    for {
      profileResult <- GetSongProfile(userID, userToken, songId).send
      popularityResult <- GetSongPopularity(userID, userToken, songId).send
    } yield {
      val genres = profileResult match {
        case (Some(profile), _) => profile.vector.map(_.GenreID)
        case (None, msg) =>
          logger.warn(s"TID=${planContext.traceID.id} -- 获取歌曲 $songId 的Profile失败: $msg")
          List.empty[String]
      }

      val popularity = popularityResult match {
        case (Some(score), _) => score
        case (None, msg) =>
          logger.warn(s"TID=${planContext.traceID.id} -- 获取歌曲 $songId 的Popularity失败: $msg")
          0.0
      }

      (genres, popularity)
    }
  }

  /**
   * 内部辅助方法：按曲风计算平均热度（纯函数）。
   * **注意**: 此方法是纯函数，不执行任何IO或日志记录，因此它不需要 PlanContext。
   */
  private def calculateAveragePopularityByGenre(songData: List[(List[String], Double)]): List[Dim] = {
    val genrePopularities: List[(String, Double)] = for {
      (genres, popularity) <- songData if genres.nonEmpty && popularity > 0
      genre <- genres
    } yield (genre, popularity)

    genrePopularities
      .groupBy(_._1)
      .view
      .mapValues { popularities =>
        val scores = popularities.map(_._2)
        if (scores.isEmpty) 0.0 else scores.sum / scores.length
      }
      .toList
      .map { case (genreId, avgPopularity) => Dim(genreId, avgPopularity) }
  }

  // **修正点**: logInfo 的签名保持不变，但在调用时需要提供上下文
  private def logInfo(message: String)(using pc: PlanContext): IO[Unit] =
    IO(logger.info(s"TID=${pc.traceID.id} -- $message"))
}