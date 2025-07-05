package APIs.StatisticsService

import Common.API.API
import Global.ServiceCenter.StatisticsServiceCode
import Objects.StatisticsService.Profile

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import io.circe.parser.*
import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}
import Objects.CreatorService.{CreatorID_Type, CreatorType} // 1. 导入新的类型

import com.fasterxml.jackson.core.`type`.TypeReference
import Common.Serialize.JacksonSerializeUtils

import scala.util.Try

import org.joda.time.DateTime
import java.util.UUID


/**
 * GetCreatorCreationTendency
 * desc: 获取指定创作者（艺术家或乐队）的创作倾向。通过 `creatorType` 参数区分目标是艺术家（"Artist"）还是乐队（"Band"）。该倾向表现为一个关于音乐曲风的归一化向量，其中每一项是 (GenreID, 倾向度) 的二元组，所有倾向度相加为1.0。
 * @param userID: String (发起请求的用户的ID，用于身份验证。)
 * @param userToken: String (用户的认证令牌。)
 * @param creator: CreatorID_Type (创作者的智能ID对象，封装了ID和类型。)
 * @return (Option[Profile], String): (创作者的创作倾向向量, 错误信息)
 */
case class GetCreatorCreationTendency(
  userID: String,
  userToken: String,
  creator: CreatorID_Type
) extends API[(Option[Profile], String)](StatisticsServiceCode)



case object GetCreatorCreationTendency{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[GetCreatorCreationTendency] = deriveEncoder
  private val circeDecoder: Decoder[GetCreatorCreationTendency] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[GetCreatorCreationTendency] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[GetCreatorCreationTendency] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[GetCreatorCreationTendency]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given getCreatorCreationTendencyEncoder: Encoder[GetCreatorCreationTendency] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given getCreatorCreationTendencyDecoder: Decoder[GetCreatorCreationTendency] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }

}