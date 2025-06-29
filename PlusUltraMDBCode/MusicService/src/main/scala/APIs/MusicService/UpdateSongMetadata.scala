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


/**
 * UpdateSongMetadata
 * desc: 更新已有的歌曲信息，包括元数据和相关引用信息。
 * @param songID: String (需要更新的歌曲ID)
 * @param userID: String (进行更新操作的用户ID)
 * @param userToken: String (用于验证用户的访问令牌)
 * @param name: String (需要更新的歌曲名称)
 * @param releaseTime: DateTime (需要更新的歌曲发布时间)
 * @param creators: String (需要更新的创作者列表)
 * @param performers: String (需要更新的演唱者列表)
 * @param lyricists: String (需要更新的作词者列表)
 * @param composers: String (需要更新的作曲者列表)
 * @param arrangers: String (需要更新的编曲者列表)
 * @param instrumentalists: String (需要更新的演奏者列表)
 * @param genres: String (需要更新的歌曲曲风列表)
 * @return result: String (操作结果的字符串描述)
 */

case class UpdateSongMetadata(
  songID: String,
  userID: String,
  userToken: String,
  name: Option[String] = None,
  releaseTime: Option[DateTime] = None,
  creators: List[String],
  performers: List[String],
  lyricists: List[String],
  composers: List[String],
  arrangers: List[String],
  instrumentalists: List[String],
  genres: List[String]
) extends API[String](MusicServiceCode)



case object UpdateSongMetadata{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[UpdateSongMetadata] = deriveEncoder
  private val circeDecoder: Decoder[UpdateSongMetadata] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[UpdateSongMetadata] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[UpdateSongMetadata] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[UpdateSongMetadata]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given updateSongMetadataEncoder: Encoder[UpdateSongMetadata] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given updateSongMetadataDecoder: Decoder[UpdateSongMetadata] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }


}

