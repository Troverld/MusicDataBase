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
 * DeleteGenre
 * desc: 删除曲风信息，仅限管理员操作
 * @param genreID: String (要删除的曲风ID)
 * @param adminID: String (管理员的唯一标识符)
 * @param adminToken: String (管理员身份验证令牌，用于权限校验)
 * @return (Boolean, String): (删除是否成功, 错误信息)
 */

case class DeleteGenre(
  genreID: String,
  adminID: String,
  adminToken: String
) extends API[(Boolean, String)](MusicServiceCode)



case object DeleteGenre{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[DeleteGenre] = deriveEncoder
  private val circeDecoder: Decoder[DeleteGenre] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[DeleteGenre] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[DeleteGenre] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[DeleteGenre]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given deleteGenreEncoder: Encoder[DeleteGenre] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given deleteGenreDecoder: Decoder[DeleteGenre] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }

}