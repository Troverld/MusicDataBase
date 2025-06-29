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
 * ApproveBandAuthRequestMessage
 * desc: 管理员审核一个绑定乐队的请求，用于管理员角色管理绑定申请
 * @param adminID: String (管理员的唯一标识)
 * @param adminToken: String (管理员的认证令牌，用于验证身份)
 * @param requestID: String (乐队绑定申请的唯一请求ID)
 * @param approve: Boolean (审核结果，true表示通过，false表示拒绝)
 * @return result: String (返回的操作结果状态字符串)
 */

case class ApproveBandAuthRequestMessage(
  adminID: String,
  adminToken: String,
  requestID: String,
  approve: Boolean
) extends API[String](OrganizeServiceCode)



case object ApproveBandAuthRequestMessage{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[ApproveBandAuthRequestMessage] = deriveEncoder
  private val circeDecoder: Decoder[ApproveBandAuthRequestMessage] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[ApproveBandAuthRequestMessage] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[ApproveBandAuthRequestMessage] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[ApproveBandAuthRequestMessage]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given approveBandAuthRequestMessageEncoder: Encoder[ApproveBandAuthRequestMessage] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given approveBandAuthRequestMessageDecoder: Decoder[ApproveBandAuthRequestMessage] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }


}

