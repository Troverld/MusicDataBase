package APIs.CreatorService

import Common.API.API
import Global.ServiceCenter.CreatorServiceCode

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
 * UpdateBandMessage
 * desc: 更新现有乐队的信息接口
 * @param userID: String (用户ID，用于标识请求用户)
 * @param userToken: String (用户令牌，用于校验用户身份)
 * @param bandID: String (乐队的唯一标识ID)
 * @param name: String (乐队的新名称（可选）)
 * @param members: String (乐队成员ID列表（可选）)
 * @param bio: String (乐队新的简介信息（可选）)
 * @return result: String (操作结果，表示更新状态)
 */

case class UpdateBandMessage(
  userID: String,
  userToken: String,
  bandID: String,
  name: Option[String] = None,
  members: List[String],
  bio: Option[String] = None
) extends API[String](CreatorServiceCode)



case object UpdateBandMessage{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[UpdateBandMessage] = deriveEncoder
  private val circeDecoder: Decoder[UpdateBandMessage] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[UpdateBandMessage] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[UpdateBandMessage] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[UpdateBandMessage]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given updateBandMessageEncoder: Encoder[UpdateBandMessage] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given updateBandMessageDecoder: Decoder[UpdateBandMessage] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }


}

