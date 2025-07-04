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
 * RateSong
 * desc: 记录用户对特定歌曲的评分。服务层将验证评分是否在1-5的有效范围内。如果用户对同一首歌曲重复评分，此操作应覆盖之前的评分。
 * @param userID: String (评分用户的ID。)
 * @param userToken: String (用户的认证令牌，用于验证身份。)
 * @param songID: String (被评分的歌曲的ID。)
 * @param rating: Int (用户给出的评分。后端服务会验证其值必须在1至5的闭区间内。)
 * @return (Boolean, String): (操作是否成功, 错误信息)
 */
case class RateSong(
  userID: String,
  userToken: String,
  songID: String,
  rating: Int
) extends API[(Boolean, String)](StatisticsServiceCode)



case object RateSong{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[RateSong] = deriveEncoder
  private val circeDecoder: Decoder[RateSong] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[RateSong] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[RateSong] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[RateSong]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given rateSongEncoder: Encoder[RateSong] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given rateSongDecoder: Decoder[RateSong] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }

}