package Impl

import Common.API.{PlanContext, Planner}
import Common.DBAPI._
import Common.Object.SqlParameter
import Common.ServiceUtils.schemaName
import APIs.OrganizeService.validateUserMapping
import APIs.CreatorService.{GetArtistByID, GetBandByID}
import APIs.MusicService.FilterSongsByEntity
import Objects.StatisticsService.Profile
import Utils.StatisticsUtils
import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory
import org.joda.time.DateTime

/**
 * Planner for GetCreatorGenreStrength: 获取创作者在各曲风下的创作实力
 *
 * @param userID      请求用户的ID
 * @param userToken   用户认证令牌
 * @param creatorID   创作者ID
 * @param creatorType 创作者类型 ("Artist" 或 "Band")
 * @param planContext 执行上下文
 */
case class GetCreatorGenreStrengthPlanner(
                                           userID: String,
                                           userToken: String,
                                           creatorID: String,
                                           creatorType: String,
                                           override val planContext: PlanContext
                                         ) extends Planner[(Option[Profile], String)] {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  override def plan(using planContext: PlanContext): IO[(Option[Profile], String)] = {
    val logic: IO[Profile] = for {
      _ <- logInfo(s"开始获取创作者 ${creatorID} (${creatorType}) 的曲风实力")

      // 步骤1: 验证用户身份
      _ <- validateUser()

      // 步骤2: 验证创作者类型
      _ <- validateCreatorType()

      // 步骤3: 验证创作者存在性
      _ <- validateCreator()

      // 步骤4: 尝试从缓存获取实力数据
      cachedStrength <- tryGetFromCache()

      // 步骤5: 如果缓存不存在或过期，实时计算实力
      strength <- cachedStrength match {
        case Some(strength) =>
          logInfo("从缓存获取创作实力成功") >> IO.pure(strength)
        case None =>
          logInfo("缓存不存在或已过期，开始实时计算创作实力") >>
          calculateAndCacheStrength()
      }

    } yield strength

    logic.map { strength =>
      (Some(strength), "获取创作实力成功")
    }.handleErrorWith { error =>
      logError(s"获取创作者 ${creatorID} 创作实力失败", error) >>
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
   * 步骤2: 验证创作者类型
   */
  private def validateCreatorType()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证创作者类型: ${creatorType}") >> {
      if (creatorType == "Artist" || creatorType == "Band") {
        logInfo("创作者类型验证通过")
      } else {
        IO.raiseError(new IllegalArgumentException(s"无效的创作者类型: ${creatorType}，只支持 'Artist' 或 'Band'"))
      }
    }
  }

  /**
   * 步骤3: 验证创作者存在性
   */
  private def validateCreator()(using PlanContext): IO[Unit] = {
    logInfo(s"正在验证创作者 ${creatorID} 是否存在") >> {
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
   * 步骤4: 尝试从缓存获取实力数据
   */
  private def tryGetFromCache()(using PlanContext): IO[Option[Profile]] = {
    for {
      _ <- logInfo("检查创作实力缓存")
      isExpired <- isStrengthCacheExpired()
      strength <- if (isExpired) {
        logInfo("缓存已过期或不存在")
        IO.pure(None)
      } else {
        logInfo("缓存有效，尝试获取")
        getStrengthFromCache()
      }
    } yield strength
  }

  /**
   * 检查创作实力缓存是否过期（超过1小时）
   */
  private def isStrengthCacheExpired()(using PlanContext): IO[Boolean] = {
    val sql = s"SELECT MAX(updated_at) FROM ${schemaName}.creator_stats_cache WHERE creator_id = ? AND creator_type = ?"
    readDBRows(sql, List(
      SqlParameter("String", creatorID),
      SqlParameter("String", creatorType)
    )).map { rows =>
      rows.headOption.map { row =>
        val lastUpdated = decodeField[Long](row, "max")
        val now = new DateTime().getMillis
        (now - lastUpdated) > 3600000 // 1小时 = 3600000毫秒
      }.getOrElse(true) // 如果没有缓存，认为已过期
    }
  }

  /**
   * 从缓存获取创作实力
   */
  private def getStrengthFromCache()(using PlanContext): IO[Option[Profile]] = {
    val sql = s"SELECT genre_id, strength_score FROM ${schemaName}.creator_stats_cache WHERE creator_id = ? AND creator_type = ? AND strength_score > 0"
    readDBRows(sql, List(
      SqlParameter("String", creatorID),
      SqlParameter("String", creatorType)
    )).map { rows =>
      if (rows.nonEmpty) {
        val vector = rows.map { row =>
          val genreId = decodeField[String](row, "genre_id")
          val strengthScore = decodeField[Double](row, "strength_score")
          (genreId, strengthScore)
        }
        Some(Profile(vector, norm = false)) // 实力分数不归一化
      } else {
        None
      }
    }
  }

  /**
   * 步骤5: 实时计算创作实力并更新缓存
   */
  private def calculateAndCacheStrength()(using PlanContext): IO[Profile] = {
    for {
      _ <- logInfo("开始实时计算创作实力")
      
      // 获取创作者的所有作品
      songs <- getCreatorSongs()
      _ <- logInfo(s"获取到创作者作品 ${songs.length} 首")
      
      // 如果没有作品，返回空实力
      strength <- if (songs.isEmpty) {
        logInfo("创作者暂无作品，返回空实力")
        IO.pure(Profile(List.empty, norm = false))
      } else {
        // 计算各曲风的平均实力
        for {
          genreStrengths <- calculateGenreStrengths(songs)
          _ <- logInfo(s"计算出曲风实力: ${genreStrengths}")
          
          profile = Profile(genreStrengths, norm = false)
          
          // 更新缓存
          _ <- updateStrengthCache(genreStrengths)
          _ <- logInfo("创作实力缓存已更新")
        } yield profile
      }
      
    } yield strength
  }

  /**
   * 获取创作者的所有作品
   */
  private def getCreatorSongs()(using PlanContext): IO[List[String]] = {
    FilterSongsByEntity(
      userID = userID,
      userToken = userToken,
      entityID = Some(creatorID),
      entityType = Some(creatorType.toLowerCase),
      genres = None
    ).send.flatMap { case (songsOpt, message) =>
      songsOpt match {
        case Some(songs) => IO.pure(songs)
        case None => 
          logInfo(s"获取创作者作品失败: $message") >> IO.pure(List.empty)
      }
    }
  }

  /**
   * 计算各曲风的实力分数
   * 实力 = 该曲风下所有作品的平均热度
   */
  private def calculateGenreStrengths(songs: List[String])(using PlanContext): IO[List[(String, Double)]] = {
    for {
      // 为每首歌获取其曲风和热度
      songData <- songs.traverse { songId =>
        for {
          genres <- getSongGenres(songId)
          popularity <- StatisticsUtils.calculateSongPopularity(songId)
        } yield (songId, genres, popularity)
      }
      
      // 按曲风分组计算平均热度
      genreStrengths = calculateAveragePopularityByGenre(songData)
      
    } yield genreStrengths
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
   * 按曲风计算平均热度
   */
  private def calculateAveragePopularityByGenre(
    songData: List[(String, List[String], Double)]
  ): List[(String, Double)] = {
    // 将每首歌的热度分配给其所有曲风
    val genrePopularities = for {
      (songId, genres, popularity) <- songData
      genre <- genres
    } yield (genre, popularity)
    
    // 按曲风分组并计算平均值
    genrePopularities
      .groupBy(_._1)
      .view.mapValues { popularities =>
        val scores = popularities.map(_._2)
        scores.sum / scores.length
      }
      .toList
  }

  /**
   * 更新创作实力缓存
   */
  private def updateStrengthCache(genreStrengths: List[(String, Double)])(using PlanContext): IO[Unit] = {
    val now = new DateTime()
    
    for {
      // 更新现有记录的strength_score，如果记录不存在则插入
      _ <- genreStrengths.traverse { case (genreId, strengthScore) =>
        for {
          // 先尝试更新
          updateSql = s"UPDATE ${schemaName}.creator_stats_cache SET strength_score = ?, updated_at = ? WHERE creator_id = ? AND creator_type = ? AND genre_id = ?"
          updateResult <- writeDB(updateSql, List(
            SqlParameter("String", strengthScore.toString),
            SqlParameter("DateTime", now.getMillis.toString),
            SqlParameter("String", creatorID),
            SqlParameter("String", creatorType),
            SqlParameter("String", genreId)
          ))
          
          // 如果更新失败（记录不存在），则插入新记录
          _ <- if (updateResult.contains("0")) {
            val insertSql = s"""INSERT INTO ${schemaName}.creator_stats_cache 
               (creator_id, creator_type, genre_id, tendency_score, strength_score, song_count, updated_at)
               VALUES (?, ?, ?, ?, ?, ?, ?)"""
            writeDB(insertSql, List(
              SqlParameter("String", creatorID),
              SqlParameter("String", creatorType),
              SqlParameter("String", genreId),
              SqlParameter("String", "0.0"), // tendency_score暂时设为0
              SqlParameter("String", strengthScore.toString),
              SqlParameter("String", "1"), // song_count暂时设为1
              SqlParameter("DateTime", now.getMillis.toString)
            ))
          } else {
            IO.unit
          }
        } yield ()
      }
    } yield ()
  }

  private def logInfo(message: String): IO[Unit] = 
    IO(logger.info(s"TID=${planContext.traceID.id} -- $message"))
    
  private def logError(message: String, cause: Throwable): IO[Unit] = 
    IO(logger.error(s"TID=${planContext.traceID.id} -- $message", cause))
}