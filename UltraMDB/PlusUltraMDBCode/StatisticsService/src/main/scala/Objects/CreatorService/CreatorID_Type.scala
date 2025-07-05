package Objects.CreatorService


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

// --- 核心定义部分 ---

/**
 * CreatorType
 * desc: 代表创作者的类型。这是一个封闭的类型集（ADT），只能是 Artist 或 Band。
 */
sealed trait CreatorType
object CreatorType {
  case object Artist extends CreatorType
  case object Band extends CreatorType

  def fromString(typeStr: String): Option[CreatorType] = typeStr.toLowerCase match {
    case "artist" => Some(Artist)
    case "band"   => Some(Band)
    case _        => None
  }
  
  def toString(creatorType: CreatorType): String = creatorType match {
    case Artist => "artist"
    case Band => "band"
  }
  
  // 为 CreatorType 提供 Circe 编解码器，以便 CreatorID_Type 可以自动派生
  // Circe 会自动将 case object 编码为字符串，例如 "Artist"
  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[CreatorType] = deriveEncoder
  private val circeDecoder: Decoder[CreatorType] = deriveDecoder

  // 自定义 Jackson 编解码器，直接处理字符串转换而不依赖 Jackson 的反射
  private val jacksonEncoder: Encoder[CreatorType] = Encoder.instance { creatorType =>
    Json.fromString(toString(creatorType))
  }

  private val jacksonDecoder: Decoder[CreatorType] = Decoder.instance { cursor =>
    cursor.as[String].flatMap { typeStr =>
      fromString(typeStr) match {
        case Some(creatorType) => Right(creatorType)
        case None => Left(io.circe.DecodingFailure(s"Invalid CreatorType: $typeStr", cursor.history))
      }
    }
  }

  // Circe + Jackson 兜底的 Encoder
  given creatorTypeEncoder: Encoder[CreatorType] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given creatorTypeDecoder: Decoder[CreatorType] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }
}

/**
 * CreatorID_Type
 * desc: 创作者ID的智能包装器，封装了创作者类型和其原始String ID。
 * @param creatorType CreatorType (创作者的类型，Artist 或 Band)
 * @param id String (原始ID字符串)
 */
case class CreatorID_Type (creatorType: CreatorType, id: String) {
  def isArtist: Boolean = creatorType == CreatorType.Artist
  def isBand: Boolean = creatorType == CreatorType.Band

  def asArtistId: Option[String] = if (isArtist) Some(id) else None
  def asBandId: Option[String] = if (isBand) Some(id) else None
  
  //process class code 预留标志位，不要删除
}

/**
 * 说明：您的示例中使用了 case object 作为伴生对象，但在Scala中，
 * 为了让编译器能自动找到隐式参数（如编解码器），
 * case class 的伴生对象应为普通的 object。此处我遵循了标准的Scala实践。
 */
case object CreatorID_Type {
  // --- 工厂方法，用于安全创建 ---
  def apply(idType: String, id: String): Try[CreatorID_Type] =
    CreatorType.fromString(idType)
      .map(t => new CreatorID_Type(t, id))
      .toRight(new IllegalArgumentException(s"无效的 CreatorType: '$idType'. 只接受 'Artist' 或 'Band'。"))
      .toTry

  def artist(id: String): CreatorID_Type = new CreatorID_Type(CreatorType.Artist, id)
  def band(id: String): CreatorID_Type = new CreatorID_Type(CreatorType.Band, id)

  // --- 完全复制您的 Circe + Jackson 兜底序列化策略 ---

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[CreatorID_Type] = deriveEncoder
  private val circeDecoder: Decoder[CreatorID_Type] = deriveDecoder

  // 修复的 Jackson 编解码器 - 不再依赖 JacksonSerializeUtils 的复杂反射
  private val jacksonEncoder: Encoder[CreatorID_Type] = Encoder.instance { creatorIdType =>
    Json.obj(
      "creatorType" -> Json.fromString(CreatorType.toString(creatorIdType.creatorType)),
      "id" -> Json.fromString(creatorIdType.id)
    )
  }

  private val jacksonDecoder: Decoder[CreatorID_Type] = Decoder.instance { cursor =>
    for {
      creatorTypeStr <- cursor.downField("creatorType").as[String]
      id <- cursor.downField("id").as[String]
      creatorType <- CreatorType.fromString(creatorTypeStr) match {
        case Some(ct) => Right(ct)
        case None => Left(io.circe.DecodingFailure(s"Invalid CreatorType: $creatorTypeStr", cursor.history))
      }
    } yield CreatorID_Type(creatorType, id)
  }
  
  // Circe + Jackson 兜底的 Encoder
  given CreatorID_TypeEncoder: Encoder[CreatorID_Type] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given CreatorID_TypeDecoder: Decoder[CreatorID_Type] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }
  
  //process object code 预留标志位，不要删除
}