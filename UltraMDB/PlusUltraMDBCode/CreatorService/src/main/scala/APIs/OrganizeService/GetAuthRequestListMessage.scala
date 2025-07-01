package APIs.OrganizeService

import Common.API.API
import Global.ServiceCenter.OrganizeServiceCode
import Objects.OrganizeService.AuthRequest
import Objects.OrganizeService.RequestStatus

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
 * GetAuthRequestListMessage
 * desc: 获取认证申请记录列表，支持分页和按状态筛选。仅限管理员调用。
 *
 * @param adminID 管理员的唯一标识。
 * @param adminToken 管理员的认证令牌。
 * @param statusFilter 可选，按类型安全的 RequestStatus 进行筛选。如果为None，则返回所有状态的记录。
 * @param pageNumber 页码，从1开始。默认为1。
 * @param pageSize 每页记录数。默认为20。
 * @return (Option[List[AuthRequest]], String): (成功时包含当前页的申请对象列表，失败时为None；附带操作信息)。
 */
case class GetAuthRequestListMessage(
  adminID: String,
  adminToken: String,
  statusFilter: Option[RequestStatus] = None, // The key change: now type-safe
  pageNumber: Int = 1,
  pageSize: Int = 20
) extends API[(Option[List[AuthRequest]], String)](OrganizeServiceCode)



case object GetAuthRequestListMessage{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[GetAuthRequestListMessage] = deriveEncoder
  private val circeDecoder: Decoder[GetAuthRequestListMessage] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[GetAuthRequestListMessage] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[GetAuthRequestListMessage] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[GetAuthRequestListMessage]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given GetAuthRequestListMessageEncoder: Encoder[GetAuthRequestListMessage] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given GetAuthRequestListMessageDecoder: Decoder[GetAuthRequestListMessage] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }

}