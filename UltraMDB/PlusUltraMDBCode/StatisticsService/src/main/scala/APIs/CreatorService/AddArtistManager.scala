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
//  * AddArtistManager
//  * desc: 
//  * @param adminID: String (发出操作的管理员ID)
//  * @param adminToken: String (发出操作的管理员令牌)
//  * @param userID: String (要加入管理者列表的用户)
//  * @param artistID: String (要新增管理者的艺术家)
//  */

// case class AddArtistManager(
//   adminID: String,
//   adminToken: String,
//   userID: String,
//   artistID: String
// ) extends API[(Boolean, String)](CreatorServiceCode)



// case object AddArtistManager{
    
//   import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

//   // Circe 默认的 Encoder 和 Decoder
//   private val circeEncoder: Encoder[AddArtistManager] = deriveEncoder
//   private val circeDecoder: Decoder[AddArtistManager] = deriveDecoder

//   // Jackson 对应的 Encoder 和 Decoder
//   private val jacksonEncoder: Encoder[AddArtistManager] = Encoder.instance { currentObj =>
//     Json.fromString(JacksonSerializeUtils.serialize(currentObj))
//   }

//   private val jacksonDecoder: Decoder[AddArtistManager] = Decoder.instance { cursor =>
//     try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[AddArtistManager]() {})) } 
//     catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
//   }
  
//   // Circe + Jackson 兜底的 Encoder
//   given addArtistManagerEncoder: Encoder[AddArtistManager] = Encoder.instance { config =>
//     Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
//   }

//   // Circe + Jackson 兜底的 Decoder
//   given addArtistManagerDecoder: Decoder[AddArtistManager] = Decoder.instance { cursor =>
//     circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
//   }

// }