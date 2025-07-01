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
 * ValidBandOwnership
 * desc: 验证某用户是否拥有对指定乐队的管理权限
 * @param userID: String (用户ID)
 * @param userToken: String (用户令牌，用于验证用户身份有效性)
 * @param bandID: String (乐队ID)
 * @return (Boolean, String): (用户是否拥有权限, 错误信息)
 */

case class ValidBandOwnership(
  userID: String,
  userToken: String,
  bandID: String
) extends API[(Boolean, String)](CreatorServiceCode)



case object ValidBandOwnership{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[ValidBandOwnership] = deriveEncoder
  private val circeDecoder: Decoder[ValidBandOwnership] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[ValidBandOwnership] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[ValidBandOwnership] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[ValidBandOwnership]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given ValidBandOwnershipEncoder: Encoder[ValidBandOwnership] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given ValidBandOwnershipDecoder: Decoder[ValidBandOwnership] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }

}