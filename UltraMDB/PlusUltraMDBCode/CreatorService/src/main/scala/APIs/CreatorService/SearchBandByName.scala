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

import Objects.CreatorService.Band

/**
 * SearchBandByName
 * desc: 根据提供的乐队ID，获取完整的乐队元数据。需要用户认证。
 * @param userID: String (发起请求的用户ID)
 * @param userToken: String (用户的认证令牌)
 * @param BandName: String (要模糊查询的乐队名称)
 * @return (Option[List[String]], String): (成功时包含乐队id列表，失败时为None；附带操作信息)
 */
case class SearchBandByName(
  userID: String,
  userToken: String,
  BandName: String
) extends API[(Option[List[String]], String)](CreatorServiceCode)

case object SearchBandByName{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[SearchBandByName] = deriveEncoder
  private val circeDecoder: Decoder[SearchBandByName] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[SearchBandByName] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[SearchBandByName] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[SearchBandByName]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given SearchBandByNameEncoder: Encoder[SearchBandByName] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given SearchBandByNameDecoder: Decoder[SearchBandByName] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }

}