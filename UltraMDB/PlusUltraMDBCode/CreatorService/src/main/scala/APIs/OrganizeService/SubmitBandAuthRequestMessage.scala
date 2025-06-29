package APIs.OrganizeService

import Common.API.API
import Global.ServiceCenter.OrganizeServiceCode

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
 * SubmitBandAuthRequestMessage
 * desc: 用户提交乐队绑定申请
 * @param userID: String (用户ID，用于标识提交申请的用户)
 * @param userToken: String (用户令牌，用于校验用户身份)
 * @param bandID: String (乐队ID，用于标识用户申请绑定的乐队)
 * @param certification: String (认证证据，用于证明用户与乐队的关联性)
 * @return (Option[String], String): (申请ID, 错误信息)
 */

case class SubmitBandAuthRequestMessage(
  userID: String,
  userToken: String,
  bandID: String,
  certification: String
) extends API[(Option[String], String)](OrganizeServiceCode)



case object SubmitBandAuthRequestMessage{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[SubmitBandAuthRequestMessage] = deriveEncoder
  private val circeDecoder: Decoder[SubmitBandAuthRequestMessage] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[SubmitBandAuthRequestMessage] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[SubmitBandAuthRequestMessage] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[SubmitBandAuthRequestMessage]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given submitBandAuthRequestMessageEncoder: Encoder[SubmitBandAuthRequestMessage] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given submitBandAuthRequestMessageDecoder: Decoder[SubmitBandAuthRequestMessage] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }

}