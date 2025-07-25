// ===== src/main/scala/APIs/StatisticsService/PurgeSongStatistics.scala (NEW FILE) =====

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
 * PurgeSongStatistics
 * desc: (管理操作) 清理与特定歌曲相关的所有统计数据，包括所有用户的评分和播放记录。
 * 这通常在歌曲从系统中被永久删除时调用。
 * @param adminID: String (管理员ID，用于验证权限。)
 * @param adminToken: String (管理员的认证令牌。)
 * @param songID: String (要清理数据的歌曲的ID。)
 * @return (Boolean, String): (操作是否成功, 附带信息)
 */
case class PurgeSongStatistics(
  adminID: String,
  adminToken: String,
  songID: String
) extends API[(Boolean, String)](StatisticsServiceCode)

case object PurgeSongStatistics {
  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[PurgeSongStatistics] = deriveEncoder
  private val circeDecoder: Decoder[PurgeSongStatistics] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[PurgeSongStatistics] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[PurgeSongStatistics] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[PurgeSongStatistics]() {})) }
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }

  // Circe + Jackson 兜底的 Encoder 和 Decoder
  given purgeSongStatisticsEncoder: Encoder[PurgeSongStatistics] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  given purgeSongStatisticsDecoder: Decoder[PurgeSongStatistics] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }
}