
package APIs.StatisticsService

import Common.API.API
import Common.Serialize.CustomColumnTypes.{decodeDateTime, encodeDateTime}
import Common.Serialize.JacksonSerializeUtils
import Global.ServiceCenter.CreatorServiceCode
import Objects.CreatorService.{CreatorID_Type, CreatorType}
import com.fasterxml.jackson.core.`type`.TypeReference
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.parser.*
import io.circe.syntax.*
import io.circe.{Decoder, Encoder, Json}
import org.joda.time.DateTime

import java.util.UUID
import scala.util.Try

/**
 * SearchCreatorByName
 * desc: 根据名称模糊搜索创作者（艺术家或乐队），支持按类型过滤和分页。
 *
 * @param userID      发起请求的用户ID。
 * @param userToken   用户的认证令牌。
 * @param name        要搜索的创作者名称。
 * @param creatorType 可选，用于过滤创作者类型 ("artist" 或 "band")。如果为None，则搜索所有类型。
 * @param pageNumber  页码，从1开始。
 * @param pageSize    每页大小。
 * @return (Option[(List[CreatorID_Type], Int)], String): 
 *         成功时为 (Some((结果列表, 总数)), 操作信息), 失败时为 (None, 错误信息)。
 */
case class SearchCreatorByName(
  userID: String,
  userToken: String,
  name: String,
  creatorType: Option[CreatorType],
  pageNumber: Int,
  pageSize: Int
) extends API[(Option[(List[CreatorID_Type], Int)], String)](CreatorServiceCode) // <-- 主要修改在这里

case object SearchCreatorByName {
  // 确保 CreatorType 和 CreatorID_Type 的自定义编解码器在作用域内
  import Common.Serialize.CustomColumnTypes.{decodeDateTime, encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[SearchCreatorByName] = deriveEncoder
  private val circeDecoder: Decoder[SearchCreatorByName] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[SearchCreatorByName] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[SearchCreatorByName] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[SearchCreatorByName]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given searchCreatorByNameEncoder: Encoder[SearchCreatorByName] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given searchCreatorByNameDecoder: Decoder[SearchCreatorByName] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }
}