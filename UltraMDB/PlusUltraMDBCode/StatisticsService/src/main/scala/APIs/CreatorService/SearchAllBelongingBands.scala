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
 * SearchAllBelongingBands
 * desc: 根据提供的艺术家ID，查询该艺术家所属的所有乐队的ID列表。
 *       这对于检查艺术家是否是任何乐队的成员等场景非常有用。
 *
 * @param userID    发起请求的用户ID，用于认证和授权。
 * @param userToken 用户的认证令牌。
 * @param artistID  要查询的艺术家的唯一ID。
 *
 * @return (Option[List[String]], String): (成功时包含乐队ID列表，如果艺术家不属于任何乐队则列表为空；失败时为None；附带操作信息)。
 *         - 成功，找到乐队: (Some(List("band_id_1", "band_id_2")), "查询成功")
 *         - 成功，未找到乐队: (Some(List()), "该艺术家不属于任何乐队")
 *         - 失败 (如艺术家不存在): (None, "指定的艺术家ID不存在")
 */
case class SearchAllBelongingBands(
  userID: String,
  userToken: String,
  artistID: String
) extends API[(Option[List[String]], String)](CreatorServiceCode)

case object SearchAllBelongingBands{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[SearchAllBelongingBands] = deriveEncoder
  private val circeDecoder: Decoder[SearchAllBelongingBands] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[SearchAllBelongingBands] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[SearchAllBelongingBands] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[SearchAllBelongingBands]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given SearchAllBelongingBandsEncoder: Encoder[SearchAllBelongingBands] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given SearchAllBelongingBandsDecoder: Decoder[SearchAllBelongingBands] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }

}