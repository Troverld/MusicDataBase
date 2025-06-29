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
 * validateAdminMapping
 * desc: 统一验证管理员的有效性、映射关系和权限，用于所有涉及管理员操作的功能作为前置验证
 * @param adminID: String (管理员ID)
 * @param adminToken: String (管理员令牌)
 * @return isValid: Boolean (验证结果，指示管理员是否通过验证)
 */

case class validateAdminMapping(
  adminID: String,
  adminToken: String
) extends API[Boolean](OrganizeServiceCode)



case object validateAdminMapping{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[validateAdminMapping] = deriveEncoder
  private val circeDecoder: Decoder[validateAdminMapping] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[validateAdminMapping] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[validateAdminMapping] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[validateAdminMapping]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given validateAdminMappingEncoder: Encoder[validateAdminMapping] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given validateAdminMappingDecoder: Decoder[validateAdminMapping] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }


}

