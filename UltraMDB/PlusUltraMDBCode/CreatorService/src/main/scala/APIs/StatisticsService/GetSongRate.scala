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
 * GetSongRate
 * desc: 查询指定用户对某首歌曲的评分。
 * @param userID: String (发起请求的用户的ID，用于身份验证。)
 * @param userToken: String (用户的认证令牌。)
 * @param targetUserID: String (被查询评分的目标用户ID。)
 * @param songID: String (被查询评分的歌曲ID。)
 * @return (Int, String): (用户的评分（1-5），如果未评分则为0；附带操作信息)
 */
case class GetSongRate(
                        userID: String,
                        userToken: String,
                        targetUserID: String,
                        songID: String
                      ) extends API[(Int, String)](StatisticsServiceCode)



case object GetSongRate {

  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[GetSongRate] = deriveEncoder
  private val circeDecoder: Decoder[GetSongRate] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[GetSongRate] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[GetSongRate] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[GetSongRate]() {})) }
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }

  // Circe + Jackson 兜底的 Encoder
  given getSongRateEncoder: Encoder[GetSongRate] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given getSongRateDecoder: Decoder[GetSongRate] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }

}