package APIs.MusicService

import Common.API.API
import Global.ServiceCenter.MusicServiceCode
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import io.circe.parser.*
import Common.Serialize.CustomColumnTypes.{decodeDateTime, encodeDateTime}
import com.fasterxml.jackson.core.`type`.TypeReference
import Common.Serialize.JacksonSerializeUtils
import Objects.CreatorService.CreatorID_Type

import scala.util.Try
import org.joda.time.DateTime

import java.util.UUID


/**
 * FilterSongsByEntity
 * desc: 按照实体（artist/band）或曲风筛选歌曲。
 *
 * @param userID: String (用户的唯一标识。)
 * @param userToken: String (用于验证用户身份的令牌。)
 * @param creator: Option[CreatorID_Type] (可选，表示筛选 creator。)
 * @param genres: Option[String] (曲风ID，可选，表示筛选曲风。)
 * @return (Option[List[String]], String): (符合条件的歌曲ID列表, 错误信息)
 */

case class FilterSongsByEntity(
                                userID: String,
                                userToken: String,
                                creator: Option[CreatorID_Type] = None,
                                genres: Option[String] = None
) extends API[(Option[List[String]], String)](MusicServiceCode)



case object FilterSongsByEntity{
    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[FilterSongsByEntity] = deriveEncoder
  private val circeDecoder: Decoder[FilterSongsByEntity] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[FilterSongsByEntity] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[FilterSongsByEntity] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[FilterSongsByEntity]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given filterSongsByEntityEncoder: Encoder[FilterSongsByEntity] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given filterSongsByEntityDecoder: Decoder[FilterSongsByEntity] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }

}