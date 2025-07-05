package APIs.MusicService

import Common.API.API
import Global.ServiceCenter.MusicServiceCode
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import io.circe.parser.*
import Common.Serialize.CustomColumnTypes.{decodeDateTime, encodeDateTime}
import com.fasterxml.jackson.core.`type`.TypeReference
import Common.Serialize.JacksonSerializeUtils
import Objects.CreatorService.CreatorID_Type

import scala.util.Try
import org.joda.time.DateTime

import java.util.UUID


/**
 * UploadNewSong
 * desc: 上传新歌曲的接口。
 * @param userID: String (用户ID，用于标识当前操作用户。)
 * @param userToken: String (用户令牌，用于验证用户身份。)
 * @param name: String (歌曲名称。)
 * @param releaseTime: DateTime (歌曲的发布日期。)
 * @param creators: List[CreatorID_Type] (创作者ID列表。)
 * @param performers: List[String] (演唱者ID列表。)
 * @param lyricists: List[String] (作词者ID列表。)
 * @param arrangers: List[String] (编曲者ID列表。)
 * @param instrumentalists: List[String] (演奏者ID列表。)
 * @param genres: List[String] (曲风ID列表，用于标识歌曲的归类。)
 * @param composers: List[String] (作曲者ID列表。)
 * @return (Option[String], String): (生成的歌曲ID, 错误信息)
 */

case class UploadNewSong(
  userID: String,
  userToken: String,
  name: String,
  releaseTime: DateTime,
  creators: List[CreatorID_Type],
  performers: List[String],
  lyricists: List[String],
  arrangers: List[String],
  instrumentalists: List[String],
  genres: List[String],
  composers: List[String]
) extends API[(Option[String], String)](MusicServiceCode)



case object UploadNewSong{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[UploadNewSong] = deriveEncoder
  private val circeDecoder: Decoder[UploadNewSong] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[UploadNewSong] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[UploadNewSong] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[UploadNewSong]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given uploadNewSongEncoder: Encoder[UploadNewSong] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given uploadNewSongDecoder: Decoder[UploadNewSong] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }

}