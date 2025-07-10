// ===== src/main/scala/APIs/StatisticsService/UnrateSong.scala (NEW FILE) =====

package APIs.StatisticsService

import Common.API.API
import Global.ServiceCenter.StatisticsServiceCode
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.parser.*
import io.circe.syntax.*
import scala.util.Try
import com.fasterxml.jackson.core.`type`.TypeReference
import Common.Serialize.JacksonSerializeUtils

/**
 * UnrateSong
 * desc: 撤销用户对特定歌曲的评分。如果用户未曾对该歌曲评分，则此操作静默成功。
 * @param userID: String (发起操作的用户的ID。)
 * @param userToken: String (用户的认证令牌，用于验证身份。)
 * @param songID: String (要撤销评分的歌曲的ID。)
 * @return (Boolean, String): (操作是否成功, 附带信息)
 */
case class UnrateSong(
  userID: String,
  userToken: String,
  songID: String
) extends API[(Boolean, String)](StatisticsServiceCode)

case object UnrateSong {
  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[UnrateSong] = deriveEncoder
  private val circeDecoder: Decoder[UnrateSong] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[UnrateSong] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[UnrateSong] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[UnrateSong]() {})) }
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }

  // Circe + Jackson 兜底的 Encoder 和 Decoder
  given unrateSongEncoder: Encoder[UnrateSong] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  given unrateSongDecoder: Decoder[UnrateSong] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }
}