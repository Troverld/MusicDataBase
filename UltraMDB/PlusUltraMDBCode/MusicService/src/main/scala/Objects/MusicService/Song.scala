package Objects.MusicService


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
 * Song
 * desc: 歌曲信息，包括ID、名称、发布时间、创作者、表演者和所属分类
 * @param songID: String (歌曲的唯一ID)
 * @param name: String (歌曲名称)
 * @param releaseTime: DateTime (歌曲的发布时间)
 * @param creators: String (歌曲的创作者)
 * @param performers: String (歌曲的表演者)
 * @param genres: String (歌曲所属的分类)
 */

case class Song(
  songID: String,
  name: String,
  releaseTime: DateTime,
  creators: List[String],
  performers: List[String],
  genres: List[String]
){

  //process class code 预留标志位，不要删除


}


case object Song{

    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[Song] = deriveEncoder
  private val circeDecoder: Decoder[Song] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[Song] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[Song] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[Song]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given songEncoder: Encoder[Song] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given songDecoder: Decoder[Song] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }



  //process object code 预留标志位，不要删除


}

