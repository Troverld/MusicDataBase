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
 * GetSimilarSongs
 * desc: 获取与指定歌曲相似的歌曲ID列表。相似度由后端根据曲风、艺术家等多种因素综合计算。
 * @param userID: String (发起请求的用户的ID，用于身份验证。)
 * @param userToken: String (用户的认证令牌。)
 * @param songID: String (作为查询基准的目标歌曲的ID。)
 * @param limit: Int (希望返回的相似歌曲的最大数量。)
 * @return (Option[List[String]], String): (相似歌曲的ID列表; 如果找不到源歌曲或无相似歌曲则为None, 错误信息)
 */
case class GetSimilarSongs(
  userID: String,
  userToken: String,
  songID: String,
  limit: Int
) extends API[(Option[List[String]], String)](StatisticsServiceCode)



case object GetSimilarSongs{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[GetSimilarSongs] = deriveEncoder
  private val circeDecoder: Decoder[GetSimilarSongs] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[GetSimilarSongs] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[GetSimilarSongs] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[GetSimilarSongs]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given getSimilarSongsEncoder: Encoder[GetSimilarSongs] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given getSimilarSongsDecoder: Decoder[GetSimilarSongs] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }

}