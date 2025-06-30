package APIs.OrganizeService

import Common.API.API
import Global.ServiceCenter.OrganizeServiceCode

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
 * validateUserMapping
 * desc: 统一验证用户的有效性和映射关系
 * @param userID: String (用户ID，用于标识当前进行验权操作的用户)
 * @param userToken: String (用户令牌，用于验证用户登录及验证有效性)
 * @return (Boolean, String): (用户ID与令牌是否匹配且有效, 错误信息)
 */

case class validateUserMapping(
  userID: String,
  userToken: String
) extends API[(Boolean, String)](OrganizeServiceCode)



case object validateUserMapping{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[validateUserMapping] = deriveEncoder
  private val circeDecoder: Decoder[validateUserMapping] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[validateUserMapping] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[validateUserMapping] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[validateUserMapping]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given validateUserMappingEncoder: Encoder[validateUserMapping] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given validateUserMappingDecoder: Decoder[validateUserMapping] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }

}
