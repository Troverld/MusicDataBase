package APIs.MusicService

import Common.API.API
import Common.Serialize.CustomColumnTypes.{decodeDateTime, encodeDateTime}
import Common.Serialize.JacksonSerializeUtils
import Global.ServiceCenter.MusicServiceCode
import com.fasterxml.jackson.core.`type`.TypeReference
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.parser.*
import io.circe.syntax.*
import io.circe.{Decoder, Encoder, Json}
import org.joda.time.DateTime

import java.util.UUID
import scala.util.Try


/**
 * SearchSongsByNamePaged
 * desc: 根据歌曲名称搜索匹配的歌曲列表，用于歌曲检索功能。
 * @param userID: String (当前用户的ID，用于权限和用户会话的匹配验证。)
 * @param userToken: String (当前用户的令牌，用于身份认证和权限验证。)
 * @param keywords: String (用户输入的模糊搜索关键词，用于匹配歌曲名称。)
 * @param pageNumber  页码，从1开始。
 * @param pageSize    每页大小。
 * @return (Option[(List[String],Int)], String): ((匹配到的歌曲ID列表, 总页码) 错误信息)
 */

case class SearchSongsByNamePaged(
  userID: String,
  userToken: String,
  keywords: String,
  pageNumber: Int,
  pageSize: Int
) extends API[(Option[(List[String],Int)], String)](MusicServiceCode)



case object SearchSongsByNamePaged{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime, encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[SearchSongsByNamePaged] = deriveEncoder
  private val circeDecoder: Decoder[SearchSongsByNamePaged] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[SearchSongsByNamePaged] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[SearchSongsByNamePaged] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[SearchSongsByNamePaged]() {})) }
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given searchSongsByNamePagedEncoder: Encoder[SearchSongsByNamePaged] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given searchSongsByNamePagedDecoder: Decoder[SearchSongsByNamePaged] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }

}