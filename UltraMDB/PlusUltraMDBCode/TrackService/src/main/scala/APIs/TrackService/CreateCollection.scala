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
 * CreateCollection
 * desc: 创建新的歌单，用于用户自定义歌单的创建
 * @param userID: String (当前操作的用户ID，确保调用者身份)
 * @param userToken: String (用户令牌，用于验证用户的合法性)
 * @param name: String (歌单的名称，不能为空)
 * @param description: String (歌单的简介，用于描述歌单的内容和信息)
 * @param contents: List[String] (歌单包含的歌曲ID列表)
 * @return (Option[String], String): (新生成的歌单ID, 错误信息)
 */

case class CreateCollection(
  userID: String,
  userToken: String,
  name: String,
  description: String,
  contents: List[String]
) extends API[(Option[String], String)](TrackServiceCode)



case object CreateCollection{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[CreateCollection] = deriveEncoder
  private val circeDecoder: Decoder[CreateCollection] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[CreateCollection] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[CreateCollection] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[CreateCollection]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given createCollectionEncoder: Encoder[CreateCollection] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given createCollectionDecoder: Decoder[CreateCollection] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }

}