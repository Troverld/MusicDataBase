// uncredited

package Impl

import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import APIs.OrganizeService.validateUserMapping
import APIs.CreatorService.{GetArtistByID, GetBandByID}
import Utils.StatisticsUtils
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory

/**
 * Planner for GetSimilarCreators: 获取与指定创作者相似的其他创作者
 *
 * @param userID      用户ID
 * @param userToken   用户认证令牌
 * @param creatorID   目标创作者ID
 * @param creatorType 创作者类型 ("Artist" 或 "Band")
 * @param limit       返回的相似创作者数量
 * @param planContext 执行上下文
 */
case class GetSimilarCreatorsPlanner(
                                      userID: String,
                                      userToken: String,
                                      creatorID: String,
                                      creatorType: String,
                                      limit: Int,
                                      override val planContext: PlanContext
                                    ) extends Planner[(Option[List[(String, String)]], String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Option[List[(String, String)]], String)] = {
    val logic: IO[List[(String, String)]] = for {
      _ <- logInfo(s"开始查找与创作者 ${creatorID} (${creatorType}) 相似的创作者，限制数量: ${limit}")

      // 步骤1: 验证用户身份
      _ <- validateUser()

      // 步骤2: 验证参数
      _ <- validateParams()

      // 步骤3: 验证目标创作者存在性
      _ <- validateTargetCreator()

      // 步骤4: 获取目标创作者的创作倾向
      targetTendency <- getCreatorTendency(creatorID, creatorType)

      // 步骤5: 查找相似创作者
      similarCreators <- findSimilarCreators(targetTendency)

      _ <- logInfo(s"找到 ${similarCreators.length} 个相似创作者")

    } yield similarCreators

    logic.map { similarCreators =>
      (Some(similarCreators), "相似创作者查找成功")
    }.handleErrorWith { error =>
      logError(s"查找创作者 ${creatorID} 的相似创作者失败", error) >>
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
   * 步骤2: 验证参数
   */
  private def validateParams()(using PlanContext): IO[Unit] = {
    logInfo(s"验证参数: creatorType=${creatorType}, limit=${limit}") >> {
      if (creatorType != "Artist" && creatorType != "Band") {
        IO.raiseError(new IllegalArgumentException(s"无效的创作者类型: ${creatorType}，只支持 'Artist' 或 'Band'"))
      } else if (limit <= 0 || limit > 50) {
        IO.raiseError(new IllegalArgumentException("相似创作者数量限制必须在1-50之间"))
      } else {
        logInfo("参数验证通过")
      }
    }
  }

  /**
   * 步骤3: 验证目标创作者存在性
   */
  private def validateTargetCreator()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证目标创作者 ${creatorID} 是否存在") >> {
      creatorType match {
        case "Artist" =>
          GetArtistByID(userID, userToken, creatorID).send.flatMap { case (artistOpt, message) =>
            artistOpt match {
              case Some(_) =>
                logInfo("艺术家存在性验证通过")
              case None =>
                IO.raiseError(new IllegalArgumentException(s"艺术家不存在: $message"))
            }
          }
        case "Band" =>
          GetBandByID(userID, userToken, creatorID).send.flatMap { case (bandOpt, message) =>
            bandOpt match {
              case Some(_) =>
                logInfo("乐队存在性验证通过")
              case None =>
                IO.raiseError(new IllegalArgumentException(s"乐队不存在: $message"))
            }
          }
      }
    }
  }

  /**
   * 步骤4: 获取目标创作者的创作倾向
   */
  private def getCreatorTendency(
    targetCreatorID: String,
    targetCreatorType: String
  )(using PlanContext): IO[List[(String, Double)]] = {
    for {
      _ <- logInfo("正在获取目标创作者的创作倾向")
      
      // 先尝试从缓存获取
      cachedTendency <- getTendencyFromCache(targetCreatorID, targetCreatorType)
      
      tendency <- cachedTendency match {
        case Some(tendency) =>
          logInfo("从缓存获取创作倾向成功") >> IO.pure(tendency)
        case None =>
          logInfo("缓存中无创作倾向数据，实时计算") >>
          calculateCreatorTendency(targetCreatorID, targetCreatorType)
      }
      
      _ <- logInfo(s"目标创作者倾向: ${tendency.take(3).map { case (g, s) => s"$g:${f"$s%.3f"}" }.mkString(", ")}...")
      
    } yield tendency
  }

  /**
   * 从缓存获取创作倾向
   */
  private def getTendencyFromCache(
    targetCreatorID: String,
    targetCreatorType: String
  )(using PlanContext): IO[Option[List[(String, Double)]]] = {
    val sql = s"SELECT genre_id, tendency_score FROM ${schemaName}.creator_stats_cache WHERE creator_id = ? AND creator_type = ? AND tendency_score > 0"
    readDBRows(sql, List(
      SqlParameter("String", targetCreatorID),
      SqlParameter("String", targetCreatorType)
    )).map { rows =>
      if (rows.nonEmpty) {
        val tendency = rows.map { row =>
          val genreId = decodeField[String](row, "genre_id")
          val tendencyScore = decodeField[Double](row, "tendency_score")
          (genreId, tendencyScore)
        }
        Some(tendency)
      } else {
        None
      }
    }
  }

  /**
   * 实时计算创作倾向
   */
  private def calculateCreatorTendency(
    targetCreatorID: String,
    targetCreatorType: String
  )(using PlanContext): IO[List[(String, Double)]] = {
    for {
      // 获取创作者的所有作品
      songs <- getCreatorSongs(targetCreatorID, targetCreatorType)
      
      // 如果没有作品，返回空倾向
      tendency <- if (songs.isEmpty) {
        logInfo("创作者暂无作品，返回空倾向")
        IO.pure(List.empty)
      } else {
        // 统计各曲风的作品分布
        for {
          genreDistribution <- calculateGenreDistribution(songs)
          normalizedDistribution = StatisticsUtils.normalizeVector(genreDistribution)
        } yield normalizedDistribution
      }
      
    } yield tendency
  }

  /**
   * 获取创作者的所有作品
   */
  private def getCreatorSongs(targetCreatorID: String, targetCreatorType: String)(using PlanContext): IO[List[String]] = {
    // 根据创作者类型查询其作品
    val sql = targetCreatorType match {
      case "Artist" =>
        s"""
        SELECT DISTINCT s.song_id
        FROM ${schemaName}.song_table s
        WHERE s.creators LIKE ? 
        OR s.performers LIKE ?
        OR s.composers LIKE ?
        OR s.lyricists LIKE ?
        OR s.arrangers LIKE ?
        OR s.instrumentalists LIKE ?
        """
      case "Band" =>
        s"""
        SELECT DISTINCT s.song_id
        FROM ${schemaName}.song_table s
        WHERE s.performers LIKE ?
        """
    }
    
    val searchPattern = s"%${targetCreatorID}%"
    val params = if (targetCreatorType == "Artist") {
      (1 to 6).map(_ => SqlParameter("String", searchPattern)).toList
    } else {
      List(SqlParameter("String", searchPattern))
    }
    
    readDBRows(sql, params).map { rows =>
      rows.map(row => decodeField[String](row, "song_id"))
    }
  }

  /**
   * 计算曲风分布
   */
  private def calculateGenreDistribution(songs: List[String])(using PlanContext): IO[List[(String, Double)]] = {
    for {
      // 为每首歌获取其曲风信息
      songGenresList <- songs.traverse(getSongGenres)
      
      // 统计每个曲风出现的次数
      genreCounts = songGenresList.flatten
        .groupBy(identity)
        .view.mapValues(_.length.toDouble)
        .toList
        
    } yield genreCounts
  }

  /**
   * 获取歌曲的曲风列表
   */
  private def getSongGenres(songId: String)(using PlanContext): IO[List[String]] = {
    val sql = s"SELECT genre_id FROM ${schemaName}.song_genre_mapping WHERE song_id = ?"
    readDBRows(sql, List(SqlParameter("String", songId))).map { rows =>
      rows.map(row => decodeField[String](row, "genre_id"))
    }
  }

  /**
   * 步骤5: 查找相似创作者
   */
  private def findSimilarCreators(targetTendency: List[(String, Double)])(using PlanContext): IO[List[(String, String)]] = {
    if (targetTendency.isEmpty) {
      logInfo("目标创作者无创作倾向数据，返回热门创作者")
      getPopularCreators()
    } else {
      for {
        // 获取所有有创作倾向数据的创作者
        allCreators <- getAllCreatorsWithTendency()
        _ <- logInfo(s"找到 ${allCreators.length} 个有创作倾向数据的创作者")
        
        // 计算相似度并排序
        similarCreators <- calculateSimilarityAndRank(targetTendency, allCreators)
        
      } yield similarCreators.take(limit)
    }
  }

  /**
   * 获取所有有创作倾向数据的创作者
   */
  private def getAllCreatorsWithTendency()(using PlanContext): IO[List[CreatorWithTendency]] = {
    val sql = s"""
      SELECT creator_id, creator_type, genre_id, tendency_score
      FROM ${schemaName}.creator_stats_cache
      WHERE creator_id != ? OR creator_type != ?
      ORDER BY creator_id, creator_type, genre_id
    """
    
    readDBRows(sql, List(
      SqlParameter("String", creatorID),
      SqlParameter("String", creatorType)
    )).map { rows =>
      // 按创作者分组
      rows.groupBy { row =>
        val cId = decodeField[String](row, "creator_id")
        val cType = decodeField[String](row, "creator_type")
        (cId, cType)
      }.map { case ((cId, cType), groupRows) =>
        val tendency = groupRows.map { row =>
          val genreId = decodeField[String](row, "genre_id")
          val tendencyScore = decodeField[Double](row, "tendency_score")
          (genreId, tendencyScore)
        }
        CreatorWithTendency(cId, cType, tendency)
      }.toList
    }
  }

  /**
   * 计算相似度并排序
   */
  private def calculateSimilarityAndRank(
    targetTendency: List[(String, Double)],
    allCreators: List[CreatorWithTendency]
  )(using PlanContext): IO[List[(String, String)]] = {
    IO {
      val similarities = allCreators.map { creator =>
        val similarity = StatisticsUtils.calculateCosineSimilarity(targetTendency, creator.tendency)
        CreatorSimilarity(creator.creatorId, creator.creatorType, similarity)
      }
      
      // 按相似度降序排序，并转换为输出格式
      similarities
        .filter(_.similarity > 0.1) // 过滤掉相似度太低的
        .sortBy(-_.similarity)
        .map(cs => (cs.creatorId, cs.creatorType))
    }
  }

  /**
   * 获取热门创作者（当目标创作者无倾向数据时的备选方案）
   */
  private def getPopularCreators()(using PlanContext): IO[List[(String, String)]] = {
    val sql = s"""
      SELECT creator_id, creator_type, AVG(strength_score) as avg_strength
      FROM ${schemaName}.creator_stats_cache
      WHERE (creator_id != ? OR creator_type != ?)
      AND strength_score > 0
      GROUP BY creator_id, creator_type
      ORDER BY avg_strength DESC
      LIMIT ?
    """
    
    readDBRows(sql, List(
      SqlParameter("String", creatorID),
      SqlParameter("String", creatorType),
      SqlParameter("String", limit.toString)
    )).map { rows =>
      rows.map { row =>
        val cId = decodeField[String](row, "creator_id")
        val cType = decodeField[String](row, "creator_type")
        (cId, cType)
      }
    }
  }

  private def logInfo(message: String): IO[Unit] = 
    IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))
    
  private def logError(message: String, cause: Throwable): IO[Unit] = 
    IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}

/**
 * 带有创作倾向的创作者
 */
case class CreatorWithTendency(
  creatorId: String,
  creatorType: String,
  tendency: List[(String, Double)]
)

/**
 * 创作者相似度
 */
case class CreatorSimilarity(
  creatorId: String,
  creatorType: String,
  similarity: Double
)