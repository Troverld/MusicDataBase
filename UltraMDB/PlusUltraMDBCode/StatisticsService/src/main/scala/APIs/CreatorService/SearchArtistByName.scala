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

import Objects.CreatorService.Artist

/**
 * SearchArtistByName
 * desc: 根据提供的艺术家ID，获取完整的艺术家元数据。需要用户认证。
 * @param userID: String (发起请求的用户ID)
 * @param userToken: String (用户的认证令牌)
 * @param artistName: String (要模糊查询的艺术家名称)
 * @return (Option[List[String]], String): (成功时包含艺术家id列表，失败时为None；附带操作信息)
 */
case class SearchArtistByName(
  userID: String,
  userToken: String,
  artistName: String
) extends API[(Option[List[String]], String)](CreatorServiceCode)

case object SearchArtistByName{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[SearchArtistByName] = deriveEncoder
  private val circeDecoder: Decoder[SearchArtistByName] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[SearchArtistByName] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[SearchArtistByName] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[SearchArtistByName]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given SearchArtistByNameEncoder: Encoder[SearchArtistByName] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given SearchArtistByNameDecoder: Decoder[SearchArtistByName] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }

}