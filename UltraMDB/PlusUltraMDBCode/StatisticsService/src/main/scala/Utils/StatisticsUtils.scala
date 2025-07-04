package Utils

import Objects.StatisticsService.Profile
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
    val vectorA = profileA.vector
    val vectorB = profileB.vector
    
    val mapA = vectorA.toMap
    val mapB = vectorB.toMap
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
    val sum = vector.map(_._2).sum
    
    val normalizedVector = if (sum == 0.0) {
      vector
    } else {
      vector.map { case (key, value) => (key, value / sum) }
    }
    
    Profile(normalizedVector, norm = true)
  }

  /**
   * 对一个带有分数的项目列表进行Softmax加权随机抽样。
   * Softmax会放大分数差异，使得分高的项有更大概率被选中，但低分项也有机会。
   *
   * @param itemsWithScores 一个包含(Item, Score)元组的列表。Item可以是任何类型（如String, Int）。
   * @tparam A Item的类型。
   * @return 抽样选中的一个Item，如果输入列表为空则返回None。
   */
  def softmaxSample[A](itemsWithScores: List[(A, Double)]): Option[A] = {
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
   * 这是softmaxSample[A]的便捷重载版本。
   * @param profile 包含(GenreID, PreferenceScore)向量的Profile。
   * @return 抽样选中的一个GenreID，如果向量为空则返回None。
   */
  def softmaxSample(profile: Profile): Option[String] = {
    softmaxSample(profile.vector)
  }
}