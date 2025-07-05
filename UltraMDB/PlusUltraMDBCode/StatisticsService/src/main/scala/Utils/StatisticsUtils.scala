package Utils

import Objects.StatisticsService.{Dim, Profile} // 确保 Dim 也被导入
import scala.math.{exp, sqrt}
import scala.util.Random

object StatisticsUtils {

  /**
   * 计算两个Profile向量的余弦相似度。
   * @param profileA Profile A
   * @param profileB Profile B
   * @return 余弦相似度值，范围 [0, 1]
   */
  def calculateCosineSimilarity(profileA: Profile, profileB: Profile): Double = {
    // 修正: List[Dim] 不能直接 toMap，需要手动转换为 Map[String, Double]
    val mapA = profileA.vector.map(dim => (dim.GenreID, dim.value)).toMap
    val mapB = profileB.vector.map(dim => (dim.GenreID, dim.value)).toMap
    
    val commonKeys = mapA.keySet.intersect(mapB.keySet)
    
    if (commonKeys.isEmpty) return 0.0
    
    val dotProduct = commonKeys.map(key => mapA.getOrElse(key, 0.0) * mapB.getOrElse(key, 0.0)).sum
    val magnitudeA = sqrt(mapA.values.map(v => v * v).sum)
    val magnitudeB = sqrt(mapB.values.map(v => v * v).sum)
    
    if (magnitudeA == 0.0 || magnitudeB == 0.0) 0.0
    else dotProduct / (magnitudeA * magnitudeB)
  }

  /**
   * 归一化一个Profile的向量，使其所有值的和为1.0。
   * 如果该Profile已经是归一化的，则直接返回。
   * @param profile 待归一化的Profile
   * @return 一个新的、向量被归一化后的Profile
   */
  def normalizeVector(profile: Profile): Profile = {
    if (profile.norm) return profile
    
    val vector = profile.vector
    // 修正: 从元组的 ._2 改为 Dim 对象的 .value
    val sum = vector.map(_.value).sum
    
    val normalizedVector = if (sum == 0.0) {
      vector
    } else {
      // 修正: 不再创建元组，而是创建新的 Dim 对象
      vector.map { dim => dim.copy(value = dim.value / sum) }
    }
    
    Profile(normalizedVector, norm = true)
  }

  /**
   * 内部实现的Softmax加权随机抽样。
   * 这个方法是私有的，只能在 StatisticsUtils 内部被调用。
   */
  private def softmaxSampleInternal[A](itemsWithScores: List[(A, Double)]): Option[A] = {
    if (itemsWithScores.isEmpty) return None
    
    val validItems = itemsWithScores.filter(_._2 > 0)
    if (validItems.isEmpty) return itemsWithScores.headOption.map(_._1)

    val expScores = validItems.map { case (_, score) => exp(score) }
    val sumExpScores = expScores.sum

    if (sumExpScores == 0 || sumExpScores.isInfinite || sumExpScores.isNaN) {
      return validItems.headOption.map(_ => validItems(Random.nextInt(validItems.length))._1)
    }

    val probabilities = expScores.map(_ / sumExpScores)
    val cumulativeProbabilities = probabilities.scanLeft(0.0)(_ + _).tail
    val randomDouble = Random.nextDouble()
    val chosenIndex = cumulativeProbabilities.indexWhere(_ >= randomDouble)

    if (chosenIndex != -1) Some(validItems(chosenIndex)._1)
    else validItems.lastOption.map(_._1)
  }

  /**
   * 对一个Profile的向量进行Softmax加权随机抽样。
   * 这是Softmax抽样的唯一公共接口。
   * @param profile 包含(GenreID, PreferenceScore)向量的Profile。
   * @return 抽样选中的一个GenreID，如果向量为空则返回None。
   */
  def softmaxSample(profile: Profile): Option[String] = {
    // 修正: 将 List[Dim] 转换为 List[(String, Double)] 以调用内部实现
    val itemsWithScores = profile.vector.map(dim => (dim.GenreID, dim.value))
    softmaxSampleInternal(itemsWithScores)
  }
}