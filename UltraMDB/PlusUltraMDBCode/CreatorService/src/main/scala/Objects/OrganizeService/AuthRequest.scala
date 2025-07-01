package Objects.OrganizeService

import Objects.CreatorService.CreatorID_Type // Import our new CreatorId type
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import com.fasterxml.jackson.core.`type`.TypeReference
import Common.Serialize.JacksonSerializeUtils
import org.joda.time.DateTime
import scala.util.Try

// --- Define the new RequestStatus ADT first ---

/**
 * RequestStatus
 * desc: 表示授权申请的当前状态。
 */
sealed trait RequestStatus
object RequestStatus {
  case object Pending extends RequestStatus
  case object Approved extends RequestStatus
  case object Rejected extends RequestStatus

  // Provide Encoder/Decoder so it can be serialized with AuthRequest
  // Circe will automatically handle case objects as strings ("Pending", "Approved", etc.)
  implicit val encoder: Encoder[RequestStatus] = deriveEncoder
  implicit val decoder: Decoder[RequestStatus] = deriveDecoder
}


// --- Define the unified AuthRequest object ---

/**
 * AuthRequest
 * desc: 一个统一的授权申请记录，用于用户申请成为艺术家或乐队的管理者。
 * @param requestID 唯一的申请ID。
 * @param userID 提交申请的用户ID。
 * @param targetID 申请绑定的目标，使用 CreatorID_Type 来区分是艺术家还是乐队。
 * @param certification 用户提供的认证材料。
 * @param status 申请的当前状态 (Pending, Approved, Rejected)。
 * @param createdAt 申请创建的时间戳。
 * @param processedBy 处理该申请的管理员ID，在处理前为None。
 * @param processedAt 处理该申请的时间戳，在处理前为None。
 */
case class AuthRequest(
  requestID: String,
  userID: String,
  targetID: CreatorID_Type,
  certification: String,
  status: RequestStatus,
  createdAt: DateTime,
  processedBy: Option[String],
  processedAt: Option[DateTime]
) {
  //process class code 预留标志位，不要删除
}

object AuthRequest {

  import Common.Serialize.CustomColumnTypes.{decodeDateTime, encodeDateTime}

  // --- Replicating your standard Circe + Jackson fallback serialization logic ---

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[AuthRequest] = deriveEncoder
  private val circeDecoder: Decoder[AuthRequest] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[AuthRequest] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[AuthRequest] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[AuthRequest]() {})) }
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }

  // Circe + Jackson 兜底的 Encoder
  given authRequestEncoder: Encoder[AuthRequest] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given authRequestDecoder: Decoder[AuthRequest] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }

  //process object code 预留标志位，不要删除
}