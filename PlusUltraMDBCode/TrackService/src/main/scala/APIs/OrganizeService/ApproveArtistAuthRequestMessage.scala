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
 * ApproveArtistAuthRequestMessage
 * desc: 管理员审核绑定艺术家的请求
 * @param adminID: String (管理员ID)
 * @param adminToken: String (管理员认证令牌)
 * @param requestID: String (绑定请求的唯一标识)
 * @param approve: Boolean (是否批准该绑定申请)
 * @return result: String (操作结果的状态信息)
 */

case class ApproveArtistAuthRequestMessage(
  adminID: String,
  adminToken: String,
  requestID: String,
  approve: Boolean
) extends API[String](OrganizeServiceCode)



case object ApproveArtistAuthRequestMessage{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[ApproveArtistAuthRequestMessage] = deriveEncoder
  private val circeDecoder: Decoder[ApproveArtistAuthRequestMessage] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[ApproveArtistAuthRequestMessage] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[ApproveArtistAuthRequestMessage] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[ApproveArtistAuthRequestMessage]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given approveArtistAuthRequestMessageEncoder: Encoder[ApproveArtistAuthRequestMessage] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given approveArtistAuthRequestMessageDecoder: Decoder[ApproveArtistAuthRequestMessage] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }


}

