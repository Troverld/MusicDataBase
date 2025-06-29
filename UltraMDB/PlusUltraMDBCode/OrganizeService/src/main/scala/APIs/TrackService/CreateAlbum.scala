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
 * CreateAlbum
 * desc: 创建新的专辑，用于管理员创建专辑
 * @param adminID: String (管理员ID，用于验证管理员身份)
 * @param adminToken: String (管理员令牌，用于验证管理员权限)
 * @param name: String (专辑名称)
 * @param description: String (专辑简介)
 * @param releaseTime: DateTime (专辑发布的时间)
 * @param creators: String (创作者ID列表，必须包含至少一个有效的Artist或Band ID)
 * @param collaborators: String (协作者ID列表，包含协助创作的Artist或Band ID)
 * @param contents: String (歌曲ID列表，表示专辑中包含的歌曲)
 * @return albumID: String (生成的专辑ID，表示新创建的专辑记录)
 */

case class CreateAlbum(
  adminID: String,
  adminToken: String,
  name: String,
  description: String,
  releaseTime: DateTime,
  creators: List[String],
  collaborators: List[String],
  contents: List[String]
) extends API[String](TrackServiceCode)



case object CreateAlbum{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[CreateAlbum] = deriveEncoder
  private val circeDecoder: Decoder[CreateAlbum] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[CreateAlbum] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[CreateAlbum] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[CreateAlbum]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given createAlbumEncoder: Encoder[CreateAlbum] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given createAlbumDecoder: Decoder[CreateAlbum] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }


}

