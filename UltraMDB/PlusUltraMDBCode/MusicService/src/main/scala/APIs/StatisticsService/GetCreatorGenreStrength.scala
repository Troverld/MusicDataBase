package APIs.StatisticsService

import Common.API.API
import Global.ServiceCenter.StatisticsServiceCode
import Objects.StatisticsService.Profile

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
 * GetCreatorGenreStrength
 * desc: 获取指定创作者（艺术家或乐队）在各个曲风下的创作实力。实力值是一个非归一化的正实数，由该创作者在该曲风下所有歌曲的热度综合计算得出。
 * @param userID: String (发起请求的用户的ID，用于身份验证。)
 * @param userToken: String (用户的认证令牌。)
 * @param creator: CreatorID_Type (创作者的智能ID对象，封装了ID和类型。)
 * @return (Option[Profile], String): (创作者的曲风实力向量, 错误信息)
 */
case class GetCreatorGenreStrength(
  userID: String,
  userToken: String,
  creator: CreatorID_Type // 使用新的 CreatorID_Type
) extends API[(Option[Profile], String)](StatisticsServiceCode)



case object GetCreatorGenreStrength{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[GetCreatorGenreStrength] = deriveEncoder
  private val circeDecoder: Decoder[GetCreatorGenreStrength] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[GetCreatorGenreStrength] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[GetCreatorGenreStrength] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[GetCreatorGenreStrength]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given getCreatorGenreStrengthEncoder: Encoder[GetCreatorGenreStrength] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given getCreatorGenreStrengthDecoder: Decoder[GetCreatorGenreStrength] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }

}