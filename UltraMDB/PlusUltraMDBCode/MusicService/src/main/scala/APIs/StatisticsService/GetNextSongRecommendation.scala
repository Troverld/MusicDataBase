package APIs.StatisticsService

import Common.API.API
import Global.ServiceCenter.StatisticsServiceCode

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
 * GetNextSongRecommendation
 * desc: 结合用户长期收听画像和当前正在播放的歌曲上下文，为用户推荐下一首歌曲。
 * @param userID: String (需要获取推荐的用户的ID。)
 * @param userToken: String (用户的认证令牌，用于验证身份。)
 * @param currentSongID: String (用户当前正在播放的歌曲的ID，作为推荐的即时上下文。)
 * @return (Option[String], String): (推荐的下一首歌曲的ID; 如果没有合适的推荐则为None, 错误信息)
 */
case class GetNextSongRecommendation(
  userID: String,
  userToken: String,
  currentSongID: String
) extends API[(Option[String], String)](StatisticsServiceCode)



case object GetNextSongRecommendation{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[GetNextSongRecommendation] = deriveEncoder
  private val circeDecoder: Decoder[GetNextSongRecommendation] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[GetNextSongRecommendation] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[GetNextSongRecommendation] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[GetNextSongRecommendation]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given getNextSongRecommendationEncoder: Encoder[GetNextSongRecommendation] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given getNextSongRecommendationDecoder: Decoder[GetNextSongRecommendation] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }

}