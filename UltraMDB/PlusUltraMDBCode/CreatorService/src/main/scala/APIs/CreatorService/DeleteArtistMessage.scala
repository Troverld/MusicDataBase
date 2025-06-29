package APIs.CreatorService

import Common.API.API
import Global.ServiceCenter.CreatorServiceCode

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
 * DeleteArtistMessage
 * desc: 删除指定艺术家信息
 * @param adminID: String (管理员ID)
 * @param adminToken: String (管理员令牌)
 * @param artistID: String (艺术家ID)
 * @return (Boolean, String): (删除是否成功, 错误信息)
 */

case class DeleteArtistMessage(
  adminID: String,
  adminToken: String,
  artistID: String
) extends API[(Boolean, String)](CreatorServiceCode)



case object DeleteArtistMessage{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[DeleteArtistMessage] = deriveEncoder
  private val circeDecoder: Decoder[DeleteArtistMessage] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[DeleteArtistMessage] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[DeleteArtistMessage] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[DeleteArtistMessage]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given deleteArtistMessageEncoder: Encoder[DeleteArtistMessage] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given deleteArtistMessageDecoder: Decoder[DeleteArtistMessage] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }

}