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
 * DeleteAlbum
 * desc: 删除专辑，仅管理员能够删除专辑。
 * @param adminID: String (管理员用户的唯一标识符)
 * @param adminToken: String (管理员身份验证令牌，用以确认操作合法性)
 * @param albumID: String (需要删除的专辑的唯一标识符)
 * @return success: Boolean (操作是否成功的布尔值)
 */

case class DeleteAlbum(
  adminID: String,
  adminToken: String,
  albumID: String
) extends API[Boolean](TrackServiceCode)



case object DeleteAlbum{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[DeleteAlbum] = deriveEncoder
  private val circeDecoder: Decoder[DeleteAlbum] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[DeleteAlbum] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[DeleteAlbum] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[DeleteAlbum]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given deleteAlbumEncoder: Encoder[DeleteAlbum] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given deleteAlbumDecoder: Decoder[DeleteAlbum] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }


}

