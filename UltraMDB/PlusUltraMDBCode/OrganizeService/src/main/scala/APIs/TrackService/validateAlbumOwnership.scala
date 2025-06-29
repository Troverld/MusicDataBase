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
 * validateAlbumOwnership
 * desc: 验证用户是否对专辑具有管理权限，用于 UpdateAlbum 和相关操作的权限验证
 * @param userID: String (用户ID，用于表示请求的用户。)
 * @param userToken: String (用户令牌，用于验证用户身份有效性)
 * @param albumID: String (专辑ID，对应需要验证权限的专辑。)
 * @return hasPermission: Boolean (一个布尔值表示用户是否具有对该专辑的管理权限)
 */

case class validateAlbumOwnership(
  userID: String,
  userToken: String,
  albumID: String
) extends API[Boolean](TrackServiceCode)



case object validateAlbumOwnership{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[validateAlbumOwnership] = deriveEncoder
  private val circeDecoder: Decoder[validateAlbumOwnership] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[validateAlbumOwnership] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[validateAlbumOwnership] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[validateAlbumOwnership]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given validateAlbumOwnershipEncoder: Encoder[validateAlbumOwnership] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given validateAlbumOwnershipDecoder: Decoder[validateAlbumOwnership] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }


}

