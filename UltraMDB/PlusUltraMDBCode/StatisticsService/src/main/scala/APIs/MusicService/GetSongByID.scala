package APIs.MusicService

import Common.API.API
import Global.ServiceCenter.MusicServiceCode

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

import Objects.MusicService.Song

/**
 * GetSongByID
 * desc: 根据提供的歌曲ID，获取完整的歌曲元数据。需要用户认证。
 * @param userID: String (发起请求的用户ID)
 * @param userToken: String (用户的认证令牌)
 * @param songID: String (要查询的歌曲的唯一ID)
 * @return (Option[Song], String): (成功时包含歌曲对象，失败时为None；附带操作信息)
 */
case class GetSongByID(
  userID: String,
  userToken: String,
  songID: String
) extends API[(Option[Song], String)](MusicServiceCode)

case object GetSongByID{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[GetSongByID] = deriveEncoder
  private val circeDecoder: Decoder[GetSongByID] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[GetSongByID] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[GetSongByID] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[GetSongByID]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given getSongByIDEncoder: Encoder[GetSongByID] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given getSongByIDDecoder: Decoder[GetSongByID] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }

}