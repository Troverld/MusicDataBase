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
 * GetAverageRating
 * desc: 查询一首歌的平均评分。
 * 该操作会返回一个包含平均评分和评分数量的元组。如果没有评分记录，则返回平均评分为0，评分数量为0。
 * @param userID: String (评分用户的ID。)
 * @param userToken: String (用户的认证令牌，用于验证身份。)
 * @param songID: String (被评分的歌曲的ID。)
 * @return ((Double,Int), String): (操作是否成功, 错误信息)
 */
case class GetAverageRating(
  userID: String,
  userToken: String,
  songID: String,
) extends API[((Double,Int), String)](StatisticsServiceCode)



case object GetAverageRating{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[GetAverageRating] = deriveEncoder
  private val circeDecoder: Decoder[GetAverageRating] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[GetAverageRating] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[GetAverageRating] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[GetAverageRating]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given GetAverageRatingEncoder: Encoder[GetAverageRating] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given GetAverageRatingDecoder: Decoder[GetAverageRating] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }

}