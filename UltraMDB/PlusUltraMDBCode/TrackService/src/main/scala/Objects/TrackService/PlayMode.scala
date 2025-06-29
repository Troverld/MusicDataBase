package Objects.TrackService

import com.fasterxml.jackson.databind.annotation.{JsonDeserialize, JsonSerialize}
import com.fasterxml.jackson.core.{JsonGenerator, JsonParser}
import com.fasterxml.jackson.databind.{DeserializationContext, JsonDeserializer, JsonSerializer, SerializerProvider}
import io.circe.{Decoder, Encoder}

@JsonSerialize(`using` = classOf[PlayModeSerializer])
@JsonDeserialize(`using` = classOf[PlayModeDeserializer])
enum PlayMode(val desc: String):

  override def toString: String = this.desc

  case Random extends PlayMode("随机播放") // 随机播放
  case LoopOne extends PlayMode("单曲循环") // 单曲循环
  case Next extends PlayMode("顺次播放") // 顺次播放
  case PauseAfterEnd extends PlayMode("播完暂停") // 播完暂停


object PlayMode:
  given encode: Encoder[PlayMode] = Encoder.encodeString.contramap[PlayMode](toString)

  given decode: Decoder[PlayMode] = Decoder.decodeString.emap(fromStringEither)

  def fromString(s: String):PlayMode  = s match
    case "随机播放" => Random
    case "单曲循环" => LoopOne
    case "顺次播放" => Next
    case "播完暂停" => PauseAfterEnd
    case _ => throw Exception(s"Unknown PlayMode: $s")

  def fromStringEither(s: String):Either[String, PlayMode]  = s match
    case "随机播放" => Right(Random)
    case "单曲循环" => Right(LoopOne)
    case "顺次播放" => Right(Next)
    case "播完暂停" => Right(PauseAfterEnd)
    case _ => Left(s"Unknown PlayMode: $s")

  def toString(t: PlayMode): String = t match
    case Random => "随机播放"
    case LoopOne => "单曲循环"
    case Next => "顺次播放"
    case PauseAfterEnd => "播完暂停"


// Jackson 序列化器
class PlayModeSerializer extends JsonSerializer[PlayMode] {
  override def serialize(value: PlayMode, gen: JsonGenerator, serializers: SerializerProvider): Unit = {
    gen.writeString(PlayMode.toString(value)) // 直接写出字符串
  }
}

// Jackson 反序列化器
class PlayModeDeserializer extends JsonDeserializer[PlayMode] {
  override def deserialize(p: JsonParser, ctxt: DeserializationContext): PlayMode = {
    PlayMode.fromString(p.getText)
  }
}

