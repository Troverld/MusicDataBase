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
 * DeleteSong
 * desc: 用于管理员删除指定歌曲信息。
 * @param songID: String (待删除的歌曲ID。)
 * @param adminID: String (管理员ID，用于进行身份验证。)
 * @param adminToken: String (管理员验证令牌，用于鉴权。)
 * @return result: String (操作的结果状态，指示成功或失败。)
 */

case class DeleteSong(
  songID: String,
  adminID: String,
  adminToken: String
) extends API[String](MusicServiceCode)



case object DeleteSong{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[DeleteSong] = deriveEncoder
  private val circeDecoder: Decoder[DeleteSong] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[DeleteSong] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[DeleteSong] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[DeleteSong]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given deleteSongEncoder: Encoder[DeleteSong] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given deleteSongDecoder: Decoder[DeleteSong] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }


}

