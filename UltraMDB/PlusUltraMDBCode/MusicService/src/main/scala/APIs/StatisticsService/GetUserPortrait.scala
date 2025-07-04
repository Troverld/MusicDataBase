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
 * GetUserPortrait
 * desc: 获取指定用户的用户画像。用户画像由一组(曲风ID, 偏好度)的元组构成，偏好度是一个(0,1)范围内的浮点数，且整个偏好度向量经过归一化处理。
 * @param userID: String (需要获取画像的目标用户的ID。)
 * @param userToken: String (用于验证请求者身份的认证令牌。)
 * @return (Option[Profile], String): (用户画像列表, 错误信息)
 */
case class GetUserPortrait(
  userID: String,
  userToken: String
) extends API[(Option[Profile], String)](StatisticsServiceCode)



case object GetUserPortrait{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[GetUserPortrait] = deriveEncoder
  private val circeDecoder: Decoder[GetUserPortrait] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[GetUserPortrait] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[GetUserPortrait] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[GetUserPortrait]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given getUserPortraitEncoder: Encoder[GetUserPortrait] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given getUserPortraitDecoder: Decoder[GetUserPortrait] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }

}