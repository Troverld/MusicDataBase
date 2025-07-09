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
 * GetMultSongsProfiles
 * desc: 根据提供的歌曲ID，获取歌曲 profile
 * @param userID: String (发起请求的用户ID)
 * @param userToken: String (用户的认证令牌)
 * @param songIDs: List[String] (要查询的所有歌曲的ID)
 * @return (Option[List[Profile]], String): (成功时包含歌曲信息，失败时为None；附带操作信息)
 */
case class GetMultSongsProfiles(
  userID: String,
  userToken: String,
  songIDs: List[String]
) extends API[(Option[List[Profile]], String)](MusicServiceCode)

case object GetMultSongsProfiles{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime, encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[GetMultSongsProfiles] = deriveEncoder
  private val circeDecoder: Decoder[GetMultSongsProfiles] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[GetMultSongsProfiles] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[GetMultSongsProfiles] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[GetMultSongsProfiles]() {})) }
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given GetMultSongsProfilesEncoder: Encoder[GetMultSongsProfiles] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given GetMultSongsProfilesDecoder: Decoder[GetMultSongsProfiles] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }

}