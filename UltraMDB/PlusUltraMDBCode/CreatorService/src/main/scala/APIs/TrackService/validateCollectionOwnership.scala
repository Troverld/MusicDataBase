package APIs.TrackService

import Common.API.API
import Global.ServiceCenter.TrackServiceCode

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
 * validateCollectionOwnership
 * desc: 验证用户是否为某歌单的所有者或维护者
 * @param userID: String (用户的唯一标识符，用于验证用户权限)
 * @param userToken: String (用户令牌，用于验证用户身份有效性)
 * @param collectionID: String (歌单的唯一标识符，用于指定某个歌单)
 * @return hasPermission: Boolean (验证结果，表示用户是否为歌单的所有者或维护者)
 */

case class validateCollectionOwnership(
  userID: String,
  userToken: String,
  collectionID: String
) extends API[Boolean](TrackServiceCode)



case object validateCollectionOwnership{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[validateCollectionOwnership] = deriveEncoder
  private val circeDecoder: Decoder[validateCollectionOwnership] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[validateCollectionOwnership] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[validateCollectionOwnership] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[validateCollectionOwnership]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given validateCollectionOwnershipEncoder: Encoder[validateCollectionOwnership] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given validateCollectionOwnershipDecoder: Decoder[validateCollectionOwnership] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }


}

