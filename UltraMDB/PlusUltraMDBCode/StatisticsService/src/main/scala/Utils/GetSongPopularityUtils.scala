// file: Utils/GetSongPopularityUtils.scala
package Utils

import Common.API.PlanContext
import cats.effect.IO
import cats.implicits._

object GetSongPopularityUtils {

  /**
   * 计算一首歌的综合热度分数。
   * 这是一个核心业务逻辑，封装了热度的计算公式。
   *
   * @param songID 要计算热度的歌曲ID。
   * @param planContext 执行上下文。
   * @return 一个包含热度分数的IO。
   */
  def calculatePopularity(songID: String)(using planContext: PlanContext): IO[Double] = {
    // 这里的 for-comprehension 就是从 GetSongPopularityPlanner 中移动过来的
    for {
      // 步骤1: 调用数据访问层获取播放统计
      playCount <- SearchUtils.fetchPlayCount(songID)

      // 步骤2: 调用数据访问层获取评分统计
      (avgRating, ratingCount) <- SearchUtils.fetchAverageRating(songID)
      
      // 步骤3: 执行业务逻辑计算
      popularity = playCount * 0.7 + avgRating * ratingCount * 0.3
      
    } yield popularity
  }

  // 未来可以添加更多类似的可复用业务流程...
  // def recommendSimilarSongs(songID: String): IO[List[Song]] = { ... }
}