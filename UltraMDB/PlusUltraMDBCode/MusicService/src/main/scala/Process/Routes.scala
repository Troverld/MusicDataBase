
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
import Impl.{CreateNewGenrePlanner, DeleteGenrePlanner, DeleteSongPlanner, FilterSongsByEntityPlanner, GetGenreList, GetSongByID, SearchSongsByNamePlanner, UpdateSongMetadataPlanner, UploadNewSongPlanner, ValidateSongOwnershipPlanner}
import Common.API.TraceID
import org.joda.time.DateTime
import org.http4s.circe.*

import java.util.UUID
import Common.Serialize.CustomColumnTypes.{decodeDateTime, encodeDateTime}

object Routes:
  val projects: TrieMap[String, Topic[IO, String]] = TrieMap.empty

  private def executePlan(messageType: String, str: String): IO[String] =
    messageType match {
      case "DeleteGenre" =>
        IO(
          decode[DeleteGenrePlanner](str) match
            case Left(err) => err.printStackTrace(); throw new Exception(s"Invalid JSON for DeleteGenre[${err.getMessage}]")
            case Right(value) => value.fullPlan.map(_.asJson.toString)
        ).flatten

      case "SearchSongsByName" =>
        IO(
          decode[SearchSongsByNamePlanner](str) match
            case Left(err) => err.printStackTrace(); throw new Exception(s"Invalid JSON for SearchSongsByName[${err.getMessage}]")
            case Right(value) => value.fullPlan.map(_.asJson.toString)
        ).flatten

      case "GetSongByID" =>
        IO(
          decode[GetSongByID](str) match
            case Left(err) => err.printStackTrace(); throw new Exception(s"Invalid JSON for GetSongsByID[${err.getMessage}]")
            case Right(value) => value.fullPlan.map(_.asJson.toString)
        ).flatten

      case "GetGenreList" =>
        IO(
          decode[GetGenreList](str) match
            case Left(err) => err.printStackTrace(); throw new Exception(s"Invalid JSON for GetGenreList[${err.getMessage}]")
            case Right(value) => value.fullPlan.map(_.asJson.toString)
        ).flatten
       
      case "DeleteSong" =>
        IO(
          decode[DeleteSongPlanner](str) match
            case Left(err) => err.printStackTrace(); throw new Exception(s"Invalid JSON for DeleteSong[${err.getMessage}]")
            case Right(value) => value.fullPlan.map(_.asJson.toString)
        ).flatten
       
      case "UpdateSongMetadata" =>
        IO(
          decode[UpdateSongMetadataPlanner](str) match
            case Left(err) => err.printStackTrace(); throw new Exception(s"Invalid JSON for UpdateSongMetadata[${err.getMessage}]")
            case Right(value) => value.fullPlan.map(_.asJson.toString)
        ).flatten
       
      case "UploadNewSong" =>
        IO(
          decode[UploadNewSongPlanner](str) match
            case Left(err) => err.printStackTrace(); throw new Exception(s"Invalid JSON for UploadNewSong[${err.getMessage}]")
            case Right(value) => value.fullPlan.map(_.asJson.toString)
        ).flatten
       
      case "FilterSongsByEntity" =>
        IO(
          decode[FilterSongsByEntityPlanner](str) match
            case Left(err) => err.printStackTrace(); throw new Exception(s"Invalid JSON for FilterSongsByEntity[${err.getMessage}]")
            case Right(value) => value.fullPlan.map(_.asJson.toString)
        ).flatten
       
      case "CreateNewGenre" =>
        IO(
          decode[CreateNewGenrePlanner](str) match
            case Left(err) => err.printStackTrace(); throw new Exception(s"Invalid JSON for CreateNewGenre[${err.getMessage}]")
            case Right(value) => value.fullPlan.map(_.asJson.toString)
        ).flatten
       
      case "ValidateSongOwnership" =>
        IO(
          decode[ValidateSongOwnershipPlanner](str) match
            case Left(err) => err.printStackTrace(); throw new Exception(s"Invalid JSON for ValidateSongOwnership[${err.getMessage}]")
            case Right(value) => value.fullPlan.map(_.asJson.toString)
        ).flatten
       

      case "test" =>
        for {
          output  <- Utils.Test.test(str)(using  PlanContext(TraceID(""), 0))
        } yield output
      case _ =>
        IO.raiseError(new Exception(s"Unknown type: $messageType"))
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
      Ok("OK")
      
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
          BadRequest(e.getMessage.asJson.toString)
      }
  }
  