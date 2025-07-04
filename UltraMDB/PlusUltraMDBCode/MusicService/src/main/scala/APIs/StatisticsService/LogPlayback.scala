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
 * LogPlayback
 * desc: 记录一次用户播放歌曲的行为。此记录将用于后续的统计分析（如用户画像生成）。播放时间戳由服务器在接收请求时生成，以确保数据的安全性和一致性。
 * @param userID: String (发起播放行为的用户的ID。)
 * @param userToken: String (用户的认证令牌，用于验证权限。)
 * @param songID: String (被播放的歌曲的ID。)
 * @return (Boolean, String): (操作是否成功, 错误信息)
 */
case class LogPlayback(
  userID: String,
  userToken: String,
  songID: String
) extends API[(Boolean, String)](StatisticsServiceCode)



case object LogPlayback{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[LogPlayback] = deriveEncoder
  private val circeDecoder: Decoder[LogPlayback] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[LogPlayback] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[LogPlayback] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[LogPlayback]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given logPlaybackEncoder: Encoder[LogPlayback] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given logPlaybackDecoder: Decoder[LogPlayback] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }

}