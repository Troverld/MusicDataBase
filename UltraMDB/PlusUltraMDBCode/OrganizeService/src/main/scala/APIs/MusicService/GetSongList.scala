package APIs.MusicService

import Common.API.API
import Common.Serialize.CustomColumnTypes.{decodeDateTime, encodeDateTime}
import Common.Serialize.JacksonSerializeUtils
import Global.ServiceCenter.MusicServiceCode
import Objects.MusicService.Song
import com.fasterxml.jackson.core.`type`.TypeReference
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.parser.*
import io.circe.syntax.*
import io.circe.{Decoder, Encoder, Json}
import org.joda.time.DateTime

import java.util.UUID
import scala.util.Try

/**
 * GetSongList
 * desc: 获取所有歌曲。需要用户认证。
 * @param userID: String (发起请求的用户ID)
 * @param userToken: String (用户的认证令牌)
 * @return (Option[List[String]], String): (成功时包含歌曲ID，失败时为None；附带操作信息)
 */
case class GetSongList(
  userID: String,
  userToken: String
) extends API[(Option[List[String]], String)](MusicServiceCode)

case object GetSongList{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime, encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[GetSongList] = deriveEncoder
  private val circeDecoder: Decoder[GetSongList] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[GetSongList] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[GetSongList] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[GetSongList]() {})) }
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given getSongByIDEncoder: Encoder[GetSongList] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given getSongByIDDecoder: Decoder[GetSongList] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }

}