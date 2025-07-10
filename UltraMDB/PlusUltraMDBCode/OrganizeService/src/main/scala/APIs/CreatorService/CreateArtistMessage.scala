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
 * CreateArtistMessage
 * desc: 创建新的艺术家信息
 * @param adminID: String (管理员ID)
 * @param adminToken: String (管理员令牌)
 * @param artistName: String (艺术家名称)
 * @param bio: String (艺术家简介)
 * @return (Option[String], String): (新建的艺术家ID, 错误信息)
 */

case class CreateArtistMessage(
  adminID: String,
  adminToken: String,
  artistName: String,
  bio: String
) extends API[(Option[String], String)](CreatorServiceCode)



case object CreateArtistMessage{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[CreateArtistMessage] = deriveEncoder
  private val circeDecoder: Decoder[CreateArtistMessage] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[CreateArtistMessage] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[CreateArtistMessage] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[CreateArtistMessage]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given createArtistMessageEncoder: Encoder[CreateArtistMessage] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given createArtistMessageDecoder: Decoder[CreateArtistMessage] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }

}