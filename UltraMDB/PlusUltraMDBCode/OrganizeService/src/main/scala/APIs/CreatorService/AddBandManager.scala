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
 * AddBandManager
 * desc: 
 * @param adminID: String (发出操作的管理员ID)
 * @param adminToken: String (发出操作的管理员令牌)
 * @param userID: String (要加入管理者列表的用户)
 * @param bandID: String (要新增管理者的乐队)
 */

case class AddBandManager(
  adminID: String,
  adminToken: String,
  userID: String,
  bandID: String
) extends API[(Boolean, String)](CreatorServiceCode)



case object AddBandManager{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[AddBandManager] = deriveEncoder
  private val circeDecoder: Decoder[AddBandManager] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[AddBandManager] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[AddBandManager] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[AddBandManager]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given addBandManagerEncoder: Encoder[AddBandManager] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given addBandManagerDecoder: Decoder[AddBandManager] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }

}