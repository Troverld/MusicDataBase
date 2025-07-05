
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
import Impl.DeleteBandMessagePlanner
import Impl.UpdateBandMessagePlanner
// import Impl.AddArtistManagerPlanner
import Impl.UpdateArtistMessagePlanner
import Impl.CreateBandMessagePlanner
import Impl.DeleteArtistMessagePlanner
import Impl.SearchAllBelongingBandsPlanner
import Impl.GetAllCreatorsPlanner
// import Impl.ValidArtistOwnershipPlanner
// import Impl.ValidBandOwnershipPlanner
// import Impl.AddBandManagerPlanner
import Impl.CreateArtistMessagePlanner
import Impl.GetArtistByIDPlanner
import Impl.GetBandByIDPlanner
import Impl.SearchArtistByNamePlanner
import Impl.SearchBandByNamePlanner
import Common.API.TraceID
import org.joda.time.DateTime
import org.http4s.circe.*
import java.util.UUID
import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

object Routes:
  val projects: TrieMap[String, Topic[IO, String]] = TrieMap.empty

  private def executePlan(messageType: String, str: String): IO[String] =
    messageType match {
      case "DeleteBandMessage" =>
        IO(
          decode[DeleteBandMessagePlanner](str) match
            case Left(err) => err.printStackTrace(); throw new Exception(s"Invalid JSON for DeleteBandMessage[${err.getMessage}]")
            case Right(value) => value.fullPlan.map(_.asJson.toString)
        ).flatten
       
      case "UpdateBandMessage" =>
        IO(
          decode[UpdateBandMessagePlanner](str) match
            case Left(err) => err.printStackTrace(); throw new Exception(s"Invalid JSON for UpdateBandMessage[${err.getMessage}]")
            case Right(value) => value.fullPlan.map(_.asJson.toString)
        ).flatten
       
      // case "AddArtistManager" =>
      //   IO(
      //     decode[AddArtistManagerPlanner](str) match
      //       case Left(err) => err.printStackTrace(); throw new Exception(s"Invalid JSON for AddArtistManager[${err.getMessage}]")
      //       case Right(value) => value.fullPlan.map(_.asJson.toString)
      //   ).flatten
       
      case "UpdateArtistMessage" =>
        IO(
          decode[UpdateArtistMessagePlanner](str) match
            case Left(err) => err.printStackTrace(); throw new Exception(s"Invalid JSON for UpdateArtistMessage[${err.getMessage}]")
            case Right(value) => value.fullPlan.map(_.asJson.toString)
        ).flatten
       
      case "CreateBandMessage" =>
        IO(
          decode[CreateBandMessagePlanner](str) match
            case Left(err) => err.printStackTrace(); throw new Exception(s"Invalid JSON for CreateBandMessage[${err.getMessage}]")
            case Right(value) => value.fullPlan.map(_.asJson.toString)
        ).flatten
       
      case "DeleteArtistMessage" =>
        IO(
          decode[DeleteArtistMessagePlanner](str) match
            case Left(err) => err.printStackTrace(); throw new Exception(s"Invalid JSON for DeleteArtistMessage[${err.getMessage}]")
            case Right(value) => value.fullPlan.map(_.asJson.toString)
        ).flatten
       
      // case "ValidArtistOwnership" =>
      //   IO(
      //     decode[ValidArtistOwnershipPlanner](str) match
      //       case Left(err) => err.printStackTrace(); throw new Exception(s"Invalid JSON for validBandOwnership[${err.getMessage}]")
      //       case Right(value) => value.fullPlan.map(_.asJson.toString)
      //   ).flatten
       
      // case "ValidBandOwnership" =>
      //   IO(
      //     decode[ValidBandOwnershipPlanner](str) match
      //       case Left(err) => err.printStackTrace(); throw new Exception(s"Invalid JSON for validBandOwnership[${err.getMessage}]")
      //       case Right(value) => value.fullPlan.map(_.asJson.toString)
      //   ).flatten
       
      // case "AddBandManager" =>
      //   IO(
      //     decode[AddBandManagerPlanner](str) match
      //       case Left(err) => err.printStackTrace(); throw new Exception(s"Invalid JSON for AddBandManager[${err.getMessage}]")
      //       case Right(value) => value.fullPlan.map(_.asJson.toString)
      //   ).flatten
       
      case "CreateArtistMessage" =>
        IO(
          decode[CreateArtistMessagePlanner](str) match
            case Left(err) => err.printStackTrace(); throw new Exception(s"Invalid JSON for CreateArtistMessage[${err.getMessage}]")
            case Right(value) => value.fullPlan.map(_.asJson.toString)
        ).flatten
       
      case "GetArtistByID" =>
        IO(
          decode[GetArtistByIDPlanner](str) match
            case Left(err) => err.printStackTrace(); throw new Exception(s"Invalid JSON for CreateArtistMessage[${err.getMessage}]")
            case Right(value) => value.fullPlan.map(_.asJson.toString)
        ).flatten
       
      case "GetBandByID" =>
        IO(
          decode[GetBandByIDPlanner](str) match
            case Left(err) => err.printStackTrace(); throw new Exception(s"Invalid JSON for CreateArtistMessage[${err.getMessage}]")
            case Right(value) => value.fullPlan.map(_.asJson.toString)
        ).flatten
       
      case "SearchArtistByName" =>
        IO(
          decode[SearchArtistByNamePlanner](str) match
            case Left(err) => err.printStackTrace(); throw new Exception(s"Invalid JSON for CreateArtistMessage[${err.getMessage}]")
            case Right(value) => value.fullPlan.map(_.asJson.toString)
        ).flatten
       
      case "SearchBandByName" =>
        IO(
          decode[SearchBandByNamePlanner](str) match
            case Left(err) => err.printStackTrace(); throw new Exception(s"Invalid JSON for CreateArtistMessage[${err.getMessage}]")
            case Right(value) => value.fullPlan.map(_.asJson.toString)
        ).flatten
       
      case "GetAllCreators" =>
        IO(
          decode[GetAllCreatorsPlanner](str) match
            case Left(err) => err.printStackTrace(); throw new Exception(s"Invalid JSON for CreateArtistMessage[${err.getMessage}]")
            case Right(value) => value.fullPlan.map(_.asJson.toString)
        ).flatten
       
      case "SearchAllBelongingBands" =>
        IO(
          decode[SearchAllBelongingBandsPlanner](str) match
            case Left(err) => err.printStackTrace(); throw new Exception(s"Invalid JSON for CreateArtistMessage[${err.getMessage}]")
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
  