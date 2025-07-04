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
 * GetSimilarCreators
 * desc: 获取与指定创作者（艺术家或乐队）曲风相近的其他创作者ID列表。相似度由后端基于创作倾向等统计数据进行计算。
 * @param userID: String (发起请求的用户的ID，用于身份验证。)
 * @param userToken: String (用户的认证令牌。)
 * @param creatorID: String (作为查询基准的目标创作者（艺术家或乐队）的ID。)
 * @param creatorType: String (目标创作者的类型。其值为 "Artist" 或 "Band"。)
 * @param limit: Int (希望返回的相似创作者的最大数量。)
 * @return (Option[List[(String,String)]], String): (相似创作者的(ID,类型)列表; 如果找不到源创作者或无相似者则为None, 错误信息)
 */
case class GetSimilarCreators(
  userID: String,
  userToken: String,
  creatorID: String,
  creatorType: String,
  limit: Int
) extends API[(Option[List[(String,String)]], String)](StatisticsServiceCode)



case object GetSimilarCreators{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[GetSimilarCreators] = deriveEncoder
  private val circeDecoder: Decoder[GetSimilarCreators] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[GetSimilarCreators] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[GetSimilarCreators] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[GetSimilarCreators]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given getSimilarCreatorsEncoder: Encoder[GetSimilarCreators] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given getSimilarCreatorsDecoder: Decoder[GetSimilarCreators] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }

}