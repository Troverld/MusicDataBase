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
 * UpdateArtistMessage
 * desc: 更新现有艺术家信息
 * @param userID: String (当前用户的ID，用于操作验证)
 * @param userToken: String (用户令牌，用于验证用户身份和权限)
 * @param artistID: String (需要更新的艺术家唯一标识)
 * @param name: Option[String] (新艺术家名称，可选)
 * @param bio: Option[String] (新艺术家简介，可选)
 * @return (Boolean, String): (更新是否成功, 错误信息)
 */

case class UpdateArtistMessage(
  userID: String,
  userToken: String,
  artistID: String,
  name: Option[String] = None,
  bio: Option[String] = None
) extends API[(Boolean, String)](CreatorServiceCode)



case object UpdateArtistMessage{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}
  given responseDecoder: Decoder[(Option[List[String]], String)] = deriveDecoder[(Option[List[String]], String)]

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[UpdateArtistMessage] = deriveEncoder
  private val circeDecoder: Decoder[UpdateArtistMessage] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[UpdateArtistMessage] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[UpdateArtistMessage] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[UpdateArtistMessage]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given updateArtistMessageEncoder: Encoder[UpdateArtistMessage] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given updateArtistMessageDecoder: Decoder[UpdateArtistMessage] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }

}