package APIs.StatisticsService

import Common.API.API
import Global.ServiceCenter.StatisticsServiceCode
// 'Song' object import is no longer needed

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
 * GetUserSongRecommendations
 * desc: 根据指定用户的用户画像，为其推荐一个歌曲ID列表。使用分页参数来控制返回结果。
 * @param userID: String (需要获取歌曲推荐的用户的ID。)
 * @param userToken: String (用户的认证令牌，用于验证身份。)
 * @param pageNumber: Int (页码，从1开始。默认为1。)
 * @param pageSize: Int (每页返回的歌曲数量。默认为20。)
 * @return (Option[List[String]], String): (推荐的歌曲ID列表, 错误信息)
 */
case class GetUserSongRecommendations(
  userID: String,
  userToken: String,
  pageNumber: Int = 1,
  pageSize: Int = 20
) extends API[(Option[List[String]], String)](StatisticsServiceCode)



case object GetUserSongRecommendations{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[GetUserSongRecommendations] = deriveEncoder
  private val circeDecoder: Decoder[GetUserSongRecommendations] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[GetUserSongRecommendations] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[GetUserSongRecommendations] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[GetUserSongRecommendations]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given getUserSongRecommendationsEncoder: Encoder[GetUserSongRecommendations] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given getUserSongRecommendationsDecoder: Decoder[GetUserSongRecommendations] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }

}