package APIs.TrackService

import Common.API.API
import Global.ServiceCenter.TrackServiceCode

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
 * AddToPlaylist
 * desc: 通过 playlistID 和 songIDs 向播放集添加歌曲，用于在用户的播放集中追加歌曲。
 * @param userID: String (执行操作的用户的唯一标识符)
 * @param userToken: String (用户的身份验证令牌，用于验证用户合法性)
 * @param playlistID: String (播放列表的唯一标识符，用于指定目标播放列表)
 * @param songIDs: List[String] (需要添加到播放列表的歌曲ID列表)
 * @return (Boolean, String): (操作是否成功, 错误信息)
 */

case class AddToPlaylist(
  userID: String,
  userToken: String,
  playlistID: String,
  songIDs: List[String]
) extends API[(Boolean, String)](TrackServiceCode)



case object AddToPlaylist{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[AddToPlaylist] = deriveEncoder
  private val circeDecoder: Decoder[AddToPlaylist] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[AddToPlaylist] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[AddToPlaylist] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[AddToPlaylist]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given addToPlaylistEncoder: Encoder[AddToPlaylist] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given addToPlaylistDecoder: Decoder[AddToPlaylist] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }

}