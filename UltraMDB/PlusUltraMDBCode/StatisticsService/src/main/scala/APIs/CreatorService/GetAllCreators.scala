package APIs.CreatorService

import Common.API.API
import Global.ServiceCenter.CreatorServiceCode
import Objects.CreatorService.CreatorID_Type // 导入 CreatorID_Type

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import com.fasterxml.jackson.core.`type`.TypeReference
import Common.Serialize.JacksonSerializeUtils
import scala.util.Try

/**
 * GetAllCreators
 * desc: 获取所有创作者（包括艺术家和乐队）的列表。
 * @param userID: String (发起请求的用户的ID，用于身份验证。)
 * @param userToken: String (用户的认证令牌。)
 * @return (Option[List[CreatorID_Type]], String): (成功时包含所有创作者的CreatorID_Type列表，失败时为None；附带操作信息)
 */
case class GetAllCreators(
  userID: String,
  userToken: String
) extends API[(Option[List[CreatorID_Type]], String)](CreatorServiceCode)


case object GetAllCreators {
    
  // 由于 CreatorID_Type 已经提供了自己的 given Encoder/Decoder，
  // Circe 的 deriveEncoder/deriveDecoder 可以自动找到它们并为 List[CreatorID_Type] 生成编解码器。
  private val circeEncoder: Encoder[GetAllCreators] = deriveEncoder
  private val circeDecoder: Decoder[GetAllCreators] = deriveDecoder

  private val jacksonEncoder: Encoder[GetAllCreators] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[GetAllCreators] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[GetAllCreators]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  given getAllCreatorsEncoder: Encoder[GetAllCreators] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  given getAllCreatorsDecoder: Decoder[GetAllCreators] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }
}