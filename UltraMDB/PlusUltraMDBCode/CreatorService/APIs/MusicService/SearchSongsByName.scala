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
 * SearchSongsByName
 * desc: 根据歌曲名称搜索匹配的歌曲列表，用于歌曲检索功能。
 * @param userID: String (当前用户的ID，用于权限和用户会话的匹配验证。)
 * @param userToken: String (当前用户的令牌，用于身份认证和权限验证。)
 * @param keywords: String (用户输入的模糊搜索关键词，用于匹配歌曲名称。)
 * @return (Option[List[String]], String): (匹配到的歌曲ID列表, 错误信息)
 */

case class SearchSongsByName(
  userID: String,
  userToken: String,
  keywords: String
) extends API[(Option[List[String]], String)](MusicServiceCode)



case object SearchSongsByName{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[SearchSongsByName] = deriveEncoder
  private val circeDecoder: Decoder[SearchSongsByName] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[SearchSongsByName] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[SearchSongsByName] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[SearchSongsByName]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given searchSongsByNameEncoder: Encoder[SearchSongsByName] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given searchSongsByNameDecoder: Decoder[SearchSongsByName] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }

}