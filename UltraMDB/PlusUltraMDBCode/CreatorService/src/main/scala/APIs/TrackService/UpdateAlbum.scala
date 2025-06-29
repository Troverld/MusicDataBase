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
 * UpdateAlbum
 * desc: 更新专辑信息，用于更新专辑的内容与元数据
 * @param userID: String (发起动作的用户ID)
 * @param userToken: String (用户令牌，用于身份验证)
 * @param albumID: String (专辑唯一标识符)
 * @param name: Option[String] (专辑名称，可选字段)
 * @param description: Option[String] (专辑简介，可选字段)
 * @param contents: List[String] (专辑包含的歌曲ID列表)
 * @param collaborators: List[String] (协作者ID列表，可选字段)
 * @return (Boolean, String): (操作是否成功, 错误信息)
 */

case class UpdateAlbum(
  userID: String,
  userToken: String,
  albumID: String,
  name: Option[String] = None,
  description: Option[String] = None,
  contents: List[String],
  collaborators: List[String]
) extends API[(Boolean, String)](TrackServiceCode)



case object UpdateAlbum{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[UpdateAlbum] = deriveEncoder
  private val circeDecoder: Decoder[UpdateAlbum] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[UpdateAlbum] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[UpdateAlbum] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[UpdateAlbum]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given updateAlbumEncoder: Encoder[UpdateAlbum] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given updateAlbumDecoder: Decoder[UpdateAlbum] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }

}