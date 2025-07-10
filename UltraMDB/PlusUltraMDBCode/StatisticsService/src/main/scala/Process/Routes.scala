package Process

import Common.API.PlanContext
import Common.DBAPI.DidRollbackException
import cats.effect.*
import fs2.concurrent.Topic
import io.circe.*
import io.circe.derivation.Configuration
import io.circe.generic.auto.*
import io.circe.parser.decode
import io.circe.syntax.*
import org.http4s.*
import org.http4s.client.Client
import org.http4s.dsl.io.*
import scala.collection.concurrent.TrieMap
import Common.Serialize.CustomColumnTypes.*
import Impl.*
import Common.API.TraceID
import org.joda.time.DateTime
import org.http4s.circe.*
import java.util.UUID
import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

object Routes:
  val projects: TrieMap[String, Topic[IO, String]] = TrieMap.empty

  private def executePlan(messageType: String, str: String): IO[String] =
    messageType match {
      // 播放记录相关
      case "LogPlayback" =>
        IO(
          decode[LogPlaybackPlanner](str) match
            case Left(err) => err.printStackTrace(); throw new Exception(s"Invalid JSON for LogPlayback[${err.getMessage}]")
            case Right(value) => value.fullPlan.map(_.asJson.toString)
        ).flatten
       
      // 歌曲评分相关
      case "RateSong" =>
        IO(
          decode[RateSongPlanner](str) match
            case Left(err) => err.printStackTrace(); throw new Exception(s"Invalid JSON for RateSong[${err.getMessage}]")
            case Right(value) => value.fullPlan.map(_.asJson.toString)
        ).flatten
       
      // 用户画像相关
      case "GetUserPortrait" =>
        IO(
          decode[GetUserPortraitPlanner](str) match
            case Left(err) => err.printStackTrace(); throw new Exception(s"Invalid JSON for GetUserPortrait[${err.getMessage}]")
            case Right(value) => value.fullPlan.map(_.asJson.toString)
        ).flatten
       
      // 歌曲热度相关
      case "GetSongPopularity" =>
        IO(
          decode[GetSongPopularityPlanner](str) match
            case Left(err) => err.printStackTrace(); throw new Exception(s"Invalid JSON for GetSongPopularity[${err.getMessage}]")
            case Right(value) => value.fullPlan.map(_.asJson.toString)
        ).flatten
       
      // 创作者创作倾向
      case "GetCreatorCreationTendency" =>
        IO(
          decode[GetCreatorCreationTendencyPlanner](str) match
            case Left(err) => err.printStackTrace(); throw new Exception(s"Invalid JSON for GetCreatorCreationTendency[${err.getMessage}]")
            case Right(value) => value.fullPlan.map(_.asJson.toString)
        ).flatten
       
      // 创作者曲风实力
      case "GetCreatorGenreStrength" =>
        IO(
          decode[GetCreatorGenreStrengthPlanner](str) match
            case Left(err) => err.printStackTrace(); throw new Exception(s"Invalid JSON for GetCreatorGenreStrength[${err.getMessage}]")
            case Right(value) => value.fullPlan.map(_.asJson.toString)
        ).flatten
       
      // 用户歌曲推荐
      case "GetUserSongRecommendations" =>
        IO(
          decode[GetUserSongRecommendationsPlanner](str) match
            case Left(err) => err.printStackTrace(); throw new Exception(s"Invalid JSON for GetUserSongRecommendations[${err.getMessage}]")
            case Right(value) => value.fullPlan.map(_.asJson.toString)
        ).flatten
       
      // 下一首歌推荐
      case "GetNextSongRecommendation" =>
        IO(
          decode[GetNextSongRecommendationPlanner](str) match
            case Left(err) => err.printStackTrace(); throw new Exception(s"Invalid JSON for GetNextSongRecommendation[${err.getMessage}]")
            case Right(value) => value.fullPlan.map(_.asJson.toString)
        ).flatten
       
      // 相似歌曲推荐
      case "GetSimilarSongs" =>
        IO(
          decode[GetSimilarSongsPlanner](str) match
            case Left(err) => err.printStackTrace(); throw new Exception(s"Invalid JSON for GetSimilarSongs[${err.getMessage}]")
            case Right(value) => value.fullPlan.map(_.asJson.toString)
        ).flatten
       
      // 相似创作者推荐
      case "GetSimilarCreators" =>
        IO(
          decode[GetSimilarCreatorsPlanner](str) match
            case Left(err) => err.printStackTrace(); throw new Exception(s"Invalid JSON for GetSimilarCreators[${err.getMessage}]")
            case Right(value) => value.fullPlan.map(_.asJson.toString)
        ).flatten

      // 获取评分
      case "GetSongRate" =>
        IO(
          decode[GetSongRatePlanner](str) match
            case Left(err) => err.printStackTrace(); throw new Exception(s"Invalid JSON for GetSongRate[${err.getMessage}]")
            case Right(value) => value.fullPlan.map(_.asJson.toString)
        ).flatten

      // 获取平均评分
      case "GetAverageRating" =>
        IO(
          decode[GetAverageRatingPlanner](str) match
            case Left(err) => err.printStackTrace(); throw new Exception(s"Invalid JSON for GetAverageRating[${err.getMessage}]")
            case Right(value) => value.fullPlan.map(_.asJson.toString)
        ).flatten

      // 移除歌曲评分
      case "UnrateSong" =>
        IO(
          decode[UnrateSongPlanner](str) match
            case Left(err) => err.printStackTrace(); throw new Exception(s"Invalid JSON for UnrateSong[${err.getMessage}]")
            case Right(value) => value.fullPlan.map(_.asJson.toString)
        ).flatten

      // 移除歌曲全部相关数据
      case "PurgeSongStatistics" =>
        IO(
          decode[PurgeSongStatisticsPlanner](str) match
            case Left(err) => err.printStackTrace(); throw new Exception(s"Invalid JSON for PurgeSongStatistics[${err.getMessage}]")
            case Right(value) => value.fullPlan.map(_.asJson.toString)
        ).flatten

      // 测试接口
      case "test" =>
        for {
          output <- Utils.Test.test(str)(using PlanContext(TraceID(""), 0))
        } yield output
        
      case _ =>
        IO.raiseError(new Exception(s"Unknown message type: $messageType"))
    }

  def handlePostRequest(req: Request[IO]): IO[String] = {
    req.as[Json].map {
      bodyJson => {
        val planContext = PlanContext(TraceID(UUID.randomUUID().toString), transactionLevel = 0)
        val planContextJson = planContext.asJson
        val updatedJson = bodyJson.deepMerge(Json.obj("planContext" -> planContextJson))
        updatedJson.toString
      }
    }
  }
  
  val service: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "health" =>
      Ok("StatisticsService is running")
      
    case GET -> Root / "stream" / projectName =>
      projects.get(projectName) match {
        case Some(topic) =>
          val stream = topic.subscribe(10)
          Ok(stream)
        case None =>
          Topic[IO, String].flatMap { topic =>
            projects.putIfAbsent(projectName, topic) match {
              case None =>
                val stream = topic.subscribe(10)
                Ok(stream)
              case Some(existingTopic) =>
                val stream = existingTopic.subscribe(10)
                Ok(stream)
            }
          }
      }
      
    case req@POST -> Root / "api" / name =>
      handlePostRequest(req).flatMap {
        executePlan(name, _)
      }.flatMap(Ok(_))
      .handleErrorWith {
        case e: DidRollbackException =>
          println(s"Rollback error: $e")
          val headers = Headers("X-DidRollback" -> "true")
          BadRequest(e.getMessage.asJson.toString).map(_.withHeaders(headers))

        case e: Throwable =>
          println(s"General error: $e")
          e.printStackTrace()
          BadRequest(e.getMessage.asJson.toString)
      }
  }