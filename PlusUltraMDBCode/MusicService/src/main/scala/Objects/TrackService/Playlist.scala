package Objects.TrackService


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
import Objects.TrackService.PlayMode

/**
 * Playlist
 * desc: 播放列表，包含当前播放的歌曲及播放模式等
 * @param playlistID: String (播放列表的唯一标识)
 * @param ownerID: String (播放列表所有者的唯一标识)
 * @param contents: String (播放列表包含的歌曲ID列表)
 * @param currentSongID: String (当前正在播放的歌曲ID)
 * @param currentPosition: Int (当前播放的具体位置（秒）)
 * @param playMode: PlayMode:1102 (当前播放模式)
 */

case class Playlist(
  playlistID: String,
  ownerID: String,
  contents: List[String],
  currentSongID: String,
  currentPosition: Int,
  playMode: PlayMode
){

  //process class code 预留标志位，不要删除


}


case object Playlist{

    
  import Common.Serialize.CustomColumnTypes.{decodeDateTime,encodeDateTime}

  // Circe 默认的 Encoder 和 Decoder
  private val circeEncoder: Encoder[Playlist] = deriveEncoder
  private val circeDecoder: Decoder[Playlist] = deriveDecoder

  // Jackson 对应的 Encoder 和 Decoder
  private val jacksonEncoder: Encoder[Playlist] = Encoder.instance { currentObj =>
    Json.fromString(JacksonSerializeUtils.serialize(currentObj))
  }

  private val jacksonDecoder: Decoder[Playlist] = Decoder.instance { cursor =>
    try { Right(JacksonSerializeUtils.deserialize(cursor.value.noSpaces, new TypeReference[Playlist]() {})) } 
    catch { case e: Throwable => Left(io.circe.DecodingFailure(e.getMessage, cursor.history)) }
  }
  
  // Circe + Jackson 兜底的 Encoder
  given playlistEncoder: Encoder[Playlist] = Encoder.instance { config =>
    Try(circeEncoder(config)).getOrElse(jacksonEncoder(config))
  }

  // Circe + Jackson 兜底的 Decoder
  given playlistDecoder: Decoder[Playlist] = Decoder.instance { cursor =>
    circeDecoder.tryDecode(cursor).orElse(jacksonDecoder.tryDecode(cursor))
  }



  //process object code 预留标志位，不要删除


}

