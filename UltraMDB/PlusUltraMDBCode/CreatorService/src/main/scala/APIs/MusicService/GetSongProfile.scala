package APIs.MusicService

import Common.API.API
import Common.Serialize.CustomColumnTypes.{decodeDateTime, encodeDateTime}
import Common.Serialize.JacksonSerializeUtils
import Global.ServiceCenter.MusicServiceCode
import Objects.MusicService.Song
import Objects.StatisticsService.Profile
import com.fasterxml.jackson.core.`type`.TypeReference
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.parser.*
import io.circe.syntax.*
import io.circe.{Decoder, Encoder, Json}
import org.joda.time.DateTime

import java.util.UUID
import scala.util.Try

/**
 * GetSongProfile
 * desc: 根据提供的歌曲ID，获取歌曲 profile
 * @param userID: String (发起请求的用户ID)
 * @param userToken: String (用户的认证令牌)
 * @param songID: String (要查询的歌曲的唯一ID)
 * @return (Option[Profile], String): (成功时包含歌曲信息，失败时为None；附带操作信息)
 */
case class GetSongProfile(
  userID: String,
  userToken: String,
  songID: String
) extends API[(Option[Profile], String)](MusicServiceCode)

case object GetSongProfile{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime, encodeDateTime}

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