package APIs.OrganizeService

import Common.API.API
import Global.ServiceCenter.OrganizeServiceCode
import Objects.CreatorService.CreatorID_Type // Import our new CreatorId type

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
 * SubmitAuthRequestMessage
 * desc: 用户提交艺术家绑定申请
 * @param userID: String (用户的唯一标识)
 * @param userToken: String (用户的令牌，用于身份验证)
 * @param targetID: CreatorID_Type (创作者的唯一标识)
 * @param certification: String (认证证据，用于验证绑定申请的合法性)
 * @return (Option[String], String): (生成的绑定申请记录的唯一标识, 错误信息)
 */

case class SubmitAuthRequestMessage(
  userID: String,
  userToken: String,
  targetID: CreatorID_Type, // 使用 CreatorID_Type 来区分是艺术家还是乐队
  certification: String
) extends API[(Option[String], String)](OrganizeServiceCode)



case object SubmitAuthRequestMessage{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[SubmitAuthRequestMessage] = deriveEncoder
  private val circeDecoder: Decoder[SubmitAuthRequestMessage] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[SubmitAuthRequestMessage] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[SubmitAuthRequestMessage] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[SubmitAuthRequestMessage]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given submitAuthRequestMessageEncoder: Encoder[SubmitAuthRequestMessage] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given submitAuthRequestMessageDecoder: Decoder[SubmitAuthRequestMessage] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }

}