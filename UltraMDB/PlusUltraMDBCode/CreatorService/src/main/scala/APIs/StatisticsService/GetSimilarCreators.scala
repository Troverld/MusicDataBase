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
import Objects.CreatorService.CreatorID_Type

import scala.util.Try

import org.joda.time.DateTime
import java.util.UUID


/**
 * GetSimilarCreators
 * desc: 获取与指定创作者（艺术家或乐队）曲风相近的其他创作者ID列表。相似度由后端基于创作倾向等统计数据进行计算。
 * @param userID: String (发起请求的用户的ID，用于身份验证。)
 * @param userToken: String (用户的认证令牌。)
 * @param creatorID: CreatorID_Type (创作者的智能ID对象，封装了ID和类型。)
 * @param limit: Int (希望返回的相似创作者的最大数量。)
 * @return (Option[List[CreatorID_Type]], String): (相似创作者的智能ID列表,如果找不到则返回 None；错误信息)
 */
case class GetSimilarCreators(
  userID: String,
  userToken: String,
  creatorID: CreatorID_Type,
  limit: Int
) extends API[(Option[List[CreatorID_Type]], String)](StatisticsServiceCode)



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