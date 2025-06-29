package Objects.TrackService


import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import io.circe.parser.*
import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

import com.fasterxml.jackson.core.`type`.TypeReference
import Common.Serialize.JacksonSerializeUtils

import scala.util.Try

import org.joda.time.DateTime
import java.util.UUID


/**
 * Album
 * desc: 专辑信息
 * @param albumID: String (专辑的唯一标识)
 * @param name: String (专辑名称)
 * @param creators: String (专辑的创建者)
 * @param collaborators: String (专辑的协作者)
 * @param releaseTime: DateTime (专辑发布时间)
 * @param description: String (专辑的详细描述)
 * @param contents: String (专辑的内容列表)
 */

case class Album(
  albumID: String,
  name: String,
  creators: List[String],
  collaborators: List[String],
  releaseTime: DateTime,
  description: Option[String] = None,
  contents: List[String]
){

  //process class code 预留标志位，不要删除


}


case object Album{

    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[Album] = deriveEncoder
  private val circeDecoder: Decoder[Album] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[Album] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[Album] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[Album]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given albumEncoder: Encoder[Album] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given albumDecoder: Decoder[Album] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }



  //process object code 预留标志位，不要删除


}

