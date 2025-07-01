package APIs.OrganizeService

import Common.API.API
import Global.ServiceCenter.OrganizeServiceCode
import Objects.OrganizeService.AuthRequest

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
 * GetRequestByID
 * desc: 根据提供的申请ID，获取完整的统一认证申请记录。仅限管理员调用。
 * @param adminID 管理员的唯一标识。
 * @param adminToken 管理员的认证令牌。
 * @param requestID 待查询的申请的唯一ID。
 * @return (Option[AuthRequest], String): (成功时包含申请对象，失败时为None；附带操作信息)。
 */
case class GetRequestByID(
  adminID: String,
  adminToken: String,
  requestID: String
) extends API[(Option[AuthRequest], String)](OrganizeServiceCode)



case object GetRequestByID{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[GetRequestByID] = deriveEncoder
  private val circeDecoder: Decoder[GetRequestByID] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[GetRequestByID] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[GetRequestByID] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[GetRequestByID]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given GetRequestByIDEncoder: Encoder[GetRequestByID] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given GetRequestByIDDecoder: Decoder[GetRequestByID] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }

}