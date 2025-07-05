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
  
  // 为 CreatorType 提供一致的小写编解码器
  // 强制使用自定义编码器，确保始终返回小写
  private val customEncoder: Encoder[CreatorType] = Encoder.instance { creatorType =>
    Json.fromString(toString(creatorType)) // 始终使用 toString 方法，确保小写
  }

  private val customDecoder: Decoder[CreatorType] = Decoder.instance { cursor =>
    cursor.as[String].flatMap { typeStr =>
      fromString(typeStr) match {
        case Some(creatorType) => Right(creatorType)
        case None => Left(io.circe.DecodingFailure(s"Invalid CreatorType: $typeStr", cursor.history))
      }
    }
  }

  // 使用统一的编解码器，不再使用 Circe 的自动派生
  given creatorTypeEncoder: Encoder[CreatorType] = customEncoder
  given creatorTypeDecoder: Decoder[CreatorType] = customDecoder
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

case object CreatorID_Type {
  // --- 工厂方法，用于安全创建 ---
  def apply(idType: String, id: String): Try[CreatorID_Type] =
    CreatorType.fromString(idType)
      .map(t => new CreatorID_Type(t, id))
      .toRight(new IllegalArgumentException(s"无效的 CreatorType: '$idType'. 只接受 'Artist' 或 'Band'。"))
      .toTry

  def artist(id: String): CreatorID_Type = new CreatorID_Type(CreatorType.Artist, id)
  def band(id: String): CreatorID_Type = new CreatorID_Type(CreatorType.Band, id)

  // --- 使用统一的编解码策略，确保一致性 ---

  // 使用自定义编解码器，确保 creatorType 字段始终是小写
  private val customEncoder: Encoder[CreatorID_Type] = Encoder.instance { creatorIdType =>
    Json.obj(
      "creatorType" -> Json.fromString(CreatorType.toString(creatorIdType.creatorType)),
      "id" -> Json.fromString(creatorIdType.id)
    )
  }

  private val customDecoder: Decoder[CreatorID_Type] = Decoder.instance { cursor =>
    for {
      creatorTypeStr <- cursor.downField("creatorType").as[String]
      id <- cursor.downField("id").as[String]
      creatorType <- CreatorType.fromString(creatorTypeStr) match {
        case Some(ct) => Right(ct)
        case None => Left(io.circe.DecodingFailure(s"Invalid CreatorType: $creatorTypeStr", cursor.history))
      }
    } yield CreatorID_Type(creatorType, id)
  }
  
  // 使用统一的编解码器，不再提供后备方案
  given CreatorID_TypeEncoder: Encoder[CreatorID_Type] = customEncoder
  given CreatorID_TypeDecoder: Decoder[CreatorID_Type] = customDecoder
  
  //process object code 预留标志位，不要删除
}