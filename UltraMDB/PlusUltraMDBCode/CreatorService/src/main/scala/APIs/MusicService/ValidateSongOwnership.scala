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
 * ValidateSongOwnership
 * desc: 验证用户是否对指定歌曲拥有管理权限
 * @param userID: String (用户ID，用于验证用户身份和权限)
 * @param userToken: String (用户令牌，用于验证用户身份有效性)
 * @param songID: String (歌曲ID，用于标识需要验证的歌曲)
 * @return (Boolean, String): (用户是否拥有管理权限, 错误信息)
 */

case class ValidateSongOwnership(
  userID: String,
  userToken: String,
  songID: String
) extends API[(Boolean, String)](MusicServiceCode)



case object ValidateSongOwnership{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[ValidateSongOwnership] = deriveEncoder
  private val circeDecoder: Decoder[ValidateSongOwnership] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[ValidateSongOwnership] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[ValidateSongOwnership] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[ValidateSongOwnership]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given validateSongOwnershipEncoder: Encoder[ValidateSongOwnership] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given validateSongOwnershipDecoder: Decoder[ValidateSongOwnership] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }

}