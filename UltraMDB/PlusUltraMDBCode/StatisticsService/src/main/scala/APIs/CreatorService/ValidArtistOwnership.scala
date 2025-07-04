// package APIs.CreatorService

// import Common.API.API
// import Global.ServiceCenter.CreatorServiceCode

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
//  * ValidArtistOwnership
//  * desc: 验证某用户是否拥有对指定艺术家的管理权限
//  * @param userID: String (用户ID)
//  * @param userToken: String (用户Token)
//  * @param artistID: String (艺术家ID)
//  * @return (Boolean, String): (用户是否拥有权限, 错误信息)
//  */

// case class ValidArtistOwnership(
//   userID: String,
//   userToken: String,
//   artistID: String
// ) extends API[(Boolean, String)](CreatorServiceCode)



// case object ValidArtistOwnership{
    
//   import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

//   // Circe 默认的 Encoder 和 Decoder
//   private val circeEncoder: Encoder[ValidArtistOwnership] = deriveEncoder
//   private val circeDecoder: Decoder[ValidArtistOwnership] = deriveDecoder

//   // Jackson 对应的 Encoder 和 Decoder
//   private val jacksonEncoder: Encoder[ValidArtistOwnership] = Encoder.instance { currentObj =>
//     Json.fromString(JacksonSerializeUtils.serialize(currentObj))
//   }

//   private val jacksonDecoder: Decoder[ValidArtistOwnership] = Decoder.instance { cursor =>
//     try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[ValidArtistOwnership]() {})) } 
//     catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
//   }
  
//   // Circe + Jackson 兜底的 Encoder
//   given ValidArtistOwnershipEncoder: Encoder[ValidArtistOwnership] = Encoder.instance { config =>
//     Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
//   }

//   // Circe + Jackson 兜底的 Decoder
//   given ValidArtistOwnershipDecoder: Decoder[ValidArtistOwnership] = Decoder.instance { cursor =>
//     circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
//   }

// }