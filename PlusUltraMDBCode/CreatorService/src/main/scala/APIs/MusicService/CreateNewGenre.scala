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
 * CreateNewGenre
 * desc: 创建一个新的曲风记录。
 * @param name: String (曲风名称。)
 * @param description: String (曲风的描述信息。)
 * @param adminID: String (管理员ID，用于验证权限。)
 * @param adminToken: String (管理员的认证令牌。)
 * @return (Option[String], String): (生成的新曲风ID, 错误信息)
 */

case class CreateNewGenre(
  name: String,
  description: String,
  adminID: String,
  adminToken: String
) extends API[(Option[String], String)](MusicServiceCode)



case object CreateNewGenre{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[CreateNewGenre] = deriveEncoder
  private val circeDecoder: Decoder[CreateNewGenre] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[CreateNewGenre] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[CreateNewGenre] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[CreateNewGenre]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given createNewGenreEncoder: Encoder[CreateNewGenre] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given createNewGenreDecoder: Decoder[CreateNewGenre] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }

}