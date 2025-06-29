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
 * validBandOwnership
 * desc: 验证某用户是否拥有对指定乐队的管理权限
 * @param userID: String (用户ID)
 * @param userToken: String (用户令牌，用于验证用户身份有效性)
 * @param bandID: String (乐队ID)
 * @return (Boolean, String): (用户是否拥有权限, 错误信息)
 */

case class validBandOwnership(
  userID: String,
  userToken: String,
  bandID: String
) extends API[(Boolean, String)](CreatorServiceCode)



case object validBandOwnership{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[validBandOwnership] = deriveEncoder
  private val circeDecoder: Decoder[validBandOwnership] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[validBandOwnership] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[validBandOwnership] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[validBandOwnership]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given validBandOwnershipEncoder: Encoder[validBandOwnership] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given validBandOwnershipDecoder: Decoder[validBandOwnership] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }

}