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
 * UpdateCollection
 * desc: 通过 collectionID 修改歌单信息，如歌单名称、简介、内容、维护者，用于对用户创建的歌单进行更新操作。
 * @param userID: String (当前操作用户的ID。)
 * @param userToken: String (当前操作用户的身份令牌，用于身份校验。)
 * @param collectionID: String (歌单ID，用于标识待更新的歌单。)
 * @param name: Option[String] (新的歌单名称，可选字段。)
 * @param description: Option[String] (新的歌单简介，可选字段。)
 * @param contents: List[String] (新的歌单内容，即包含的歌曲ID列表，可选字段。)
 * @param maintainers: List[String] (新的维护者ID列表，可选字段。)
 * @return (Boolean, String): (操作是否成功, 错误信息)
 */

case class UpdateCollection(
  userID: String,
  userToken: String,
  collectionID: String,
  name: Option[String] = None,
  description: Option[String] = None,
  contents: Option[List[String]] = None,
  maintainers: Option[List[String]] = None
) extends API[(Boolean, String)](TrackServiceCode)



case object UpdateCollection{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[UpdateCollection] = deriveEncoder
  private val circeDecoder: Decoder[UpdateCollection] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[UpdateCollection] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[UpdateCollection] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[UpdateCollection]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given updateCollectionEncoder: Encoder[UpdateCollection] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given updateCollectionDecoder: Decoder[UpdateCollection] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }

}