package Utils

import Common.API.PlanContext
import APIs.MusicService.{FilterSongsByEntity, GetMultSongsProfiles}
import Objects.CreatorService.CreatorID_Type
import Objects.StatisticsService.{Dim, Profile}
import cats.effect.IO
import cats.implicits._
import org.slf4j.LoggerFactory
import io.circe.generic.auto._

object GetCreatorCreationTendencyUtils {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  /**
   * 生成创作者的创作倾向画像。
   * 这是核心业务逻辑，封装了获取作品、分析曲风和聚合计算的全过程。
   */
  def generateTendencyProfile(creator: CreatorID_Type, userID: String, userToken: String)(using planContext: PlanContext): IO[Profile] = {
    for {
      _ <- logInfo(s"在Utils层开始计算创作者 ${creator.id} 的创作倾向")
      
      // 步骤1: 获取创作者的所有作品ID
      songs <- getCreatorSongs(creator, userID, userToken)
      _ <- logInfo(s"获取到创作者作品 ${songs.length} 首")

      profile <- if (songs.isEmpty) {
        logInfo("创作者暂无作品，返回空倾向") >>
          IO.pure(Profile(List.empty, norm = true))
      } else {
        // 步骤2 & 3: 计算曲风分布并归一化
        for {
          unnormalizedProfile <- calculateGenreDistribution(songs, userID, userToken)
          _ <- logInfo(s"计算出未归一化的曲风分布: ${unnormalizedProfile.vector}")
          normalizedProfile = StatisticsUtils.normalizeVector(unnormalizedProfile)
        } yield normalizedProfile
      }
    } yield profile
  }

  /**
   * 内部辅助方法：调用API获取创作者的所有作品ID。
   */
  private def getCreatorSongs(creator: CreatorID_Type, userID: String, userToken: String)(using planContext: PlanContext): IO[List[String]] = {
    FilterSongsByEntity(userID, userToken, Some(creator)).send.flatMap {
      case (Some(songs), _) => IO.pure(songs)
      case (None, message) =>
        logInfo(s"获取创作者作品失败: $message. 将视为空列表处理。") >> IO.pure(List.empty)
    }
  }

  /**
   * 内部辅助方法：并行获取所有歌曲的曲风并聚合成一个未归一化的Profile。
   */
  private def calculateGenreDistribution(songs: List[String], userID: String, userToken: String)(using planContext: PlanContext): IO[Profile] = {
    // 如果没有歌曲，无需发起API调用
    if (songs.isEmpty) {
      return IO.pure(Profile(List.empty, norm = false))
    }

    // 使用批量API一次性获取所有歌曲的Profile
    GetMultSongsProfiles(userID, userToken, songs).send.flatMap {
      // 成功获取到Profile列表
      case (Some(profiles), _) =>
        IO {
          // 从所有Profile中提取出所有的Dim
          val allDims = profiles.flatMap(_.vector)

          // 聚合所有曲风
          val genreCounts = allDims
            .groupBy(_.GenreID)
            .view.mapValues(dims => dims.map(_.value).sum)
            .toList
            .map { case (genreId, count) => Dim(genreId, count) }

          Profile(genreCounts, norm = false)
        }

      // 批量API调用失败
      case (None, message) =>
        logInfo(s"批量获取歌曲Profile失败: $message. 将返回空分布。") >>
          IO.pure(Profile(List.empty, norm = false))
    }
  }

  private def logInfo(message: String)(using pc: PlanContext): IO[Unit] =
    IO(logger.info(s"TID=${pc.traceID.id} -- $message"))
}