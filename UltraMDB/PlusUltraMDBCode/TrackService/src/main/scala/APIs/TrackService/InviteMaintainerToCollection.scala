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
 * InviteMaintainerToCollection
 * desc: 邀请某用户成为歌单的维护者，用于扩展歌单管理权限。
 * @param collectionID: String (歌单对应的唯一标识符。)
 * @param userID: String (当前发起邀请操作的用户ID，需要拥有歌单的管理权限。)
 * @param userToken: String (当前用户的API访问令牌，用于验证操作合法性。)
 * @param invitedUserID: String (被邀请的用户ID，新的歌单维护者。)
 * @return success: Boolean (操作的成功状态，true表示成功，false表示失败。)
 */

case class InviteMaintainerToCollection(
  collectionID: String,
  userID: String,
  userToken: String,
  invitedUserID: String
) extends API[Boolean](TrackServiceCode)



case object InviteMaintainerToCollection{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[InviteMaintainerToCollection] = deriveEncoder
  private val circeDecoder: Decoder[InviteMaintainerToCollection] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[InviteMaintainerToCollection] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[InviteMaintainerToCollection] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[InviteMaintainerToCollection]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given inviteMaintainerToCollectionEncoder: Encoder[InviteMaintainerToCollection] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given inviteMaintainerToCollectionDecoder: Decoder[InviteMaintainerToCollection] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }


}

