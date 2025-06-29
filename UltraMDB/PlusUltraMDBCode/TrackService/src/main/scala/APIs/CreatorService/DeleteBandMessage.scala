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
 * DeleteBandMessage
 * desc: 删除指定乐队信息
 * @param adminID: String (管理员ID，确保操作者身份为有权限用户。)
 * @param adminToken: String (管理员令牌，确保本次操作的有效性。)
 * @param bandID: String (待删除的乐队ID。)
 * @return (Boolean, String): (删除是否成功, 错误信息)
 */

case class DeleteBandMessage(
  adminID: String,
  adminToken: String,
  bandID: String
) extends API[(Boolean, String)](CreatorServiceCode)



case object DeleteBandMessage{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[DeleteBandMessage] = deriveEncoder
  private val circeDecoder: Decoder[DeleteBandMessage] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[DeleteBandMessage] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[DeleteBandMessage] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[DeleteBandMessage]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given deleteBandMessageEncoder: Encoder[DeleteBandMessage] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given deleteBandMessageDecoder: Decoder[DeleteBandMessage] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }

}