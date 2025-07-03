// package APIs.OrganizeService

// import Common.API.API
// import Global.ServiceCenter.OrganizeServiceCode

// import io.circe.{Decoder, Encoder, Json}
// import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
// import io.circe.syntax.*
// import io.circe.parser.*
// import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

// import com.fasterxml.jackson.core.`type`.TypeReference
// import Common.Serialize.JacksonSerializeUtils

// import scala.util.Try

// import org.joda.time.DateTime
// import java.util.UUID


// /**
//  * ApproveAuthRequestMessage
//  * desc: 管理员审核绑定创作者的请求
//  * @param adminID: String (管理员ID)
//  * @param adminToken: String (管理员认证令牌)
//  * @param requestID: String (绑定请求的唯一标识)
//  * @param approve: Boolean (是否批准该绑定申请)
//  * @return (Boolean, String): (审核是否成功, 错误信息)
//  */

// case class ApproveAuthRequestMessage(
//   adminID: String,
//   adminToken: String,
//   requestID: String,
//   approve: Boolean
// ) extends API[(Boolean, String)](OrganizeServiceCode)



// case object ApproveAuthRequestMessage{
    
//   import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

//   // Circe 默认的 Encoder 和 Decoder
//   private val circeEncoder: Encoder[ApproveAuthRequestMessage] = deriveEncoder
//   private val circeDecoder: Decoder[ApproveAuthRequestMessage] = deriveDecoder

//   // Jackson 对应的 Encoder 和 Decoder
//   private val jacksonEncoder: Encoder[ApproveAuthRequestMessage] = Encoder.instance { currentObj =>
//     Json.fromString(JacksonSerializeUtils.serialize(currentObj))
//   }

//   private val jacksonDecoder: Decoder[ApproveAuthRequestMessage] = Decoder.instance { cursor =>
//     try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[ApproveAuthRequestMessage]() {})) } 
//     catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
//   }
  
//   // Circe + Jackson 兜底的 Encoder
//   given approveAuthRequestMessageEncoder: Encoder[ApproveAuthRequestMessage] = Encoder.instance { config =>
//     Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
//   }

//   // Circe + Jackson 兜底的 Decoder
//   given approveAuthRequestMessageDecoder: Decoder[ApproveAuthRequestMessage] = Decoder.instance { cursor =>
//     circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
//   }

// }