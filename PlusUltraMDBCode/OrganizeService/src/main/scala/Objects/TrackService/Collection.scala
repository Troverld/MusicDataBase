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
 * Collection
 * desc: 集合信息, 用于表示专辑集、歌单等，包含集合的基本信息
 * @param collectionID: String (集合的唯一ID)
 * @param name: String (集合的名字)
 * @param ownerID: String (集合创建者的唯一ID)
 * @param maintainers: String (集合维护者的用户ID列表)
 * @param uploadTime: DateTime (集合的上传时间)
 * @param description: String (集合的描述信息)
 * @param contents: String (集合中包含的内容ID列表)
 */

case class Collection(
  collectionID: String,
  name: String,
  ownerID: String,
  maintainers: List[String],
  uploadTime: DateTime,
  description: String,
  contents: List[String]
){

  //process class code 预留标志位，不要删除


}


case object Collection{

    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[Collection] = deriveEncoder
  private val circeDecoder: Decoder[Collection] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[Collection] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[Collection] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[Collection]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given collectionEncoder: Encoder[Collection] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given collectionDecoder: Decoder[Collection] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }



  //process object code 预留标志位，不要删除


}

