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
 * UserRegisterMessage
 * desc: 用户注册操作接口，用户通过用户名和密码完成注册，并分配唯一ID
 * @param userName: String (用户名，用于标识唯一用户账户)
 * @param password: String (用户密码，用于账户安全验证，需加密存储)
 * @return result: String (注册结果状态，返回操作的结果信息)
 */

case class UserRegisterMessage(
  userName: String,
  password: String
) extends API[String](OrganizeServiceCode)



case object UserRegisterMessage{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[UserRegisterMessage] = deriveEncoder
  private val circeDecoder: Decoder[UserRegisterMessage] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[UserRegisterMessage] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[UserRegisterMessage] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[UserRegisterMessage]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given userRegisterMessageEncoder: Encoder[UserRegisterMessage] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given userRegisterMessageDecoder: Decoder[UserRegisterMessage] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }


}

