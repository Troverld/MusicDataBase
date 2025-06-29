package Objects.OrganizeService

import com.fasterxml.jackson.databind.annotation.{JsonDeserialize, JsonSerialize}
import com.fasterxml.jackson.core.{JsonGenerator, JsonParser}
import com.fasterxml.jackson.databind.{DeserializationContext, JsonDeserializer, JsonSerializer, SerializerProvider}
import io.circe.{Decoder, Encoder}

@JsonSerialize(`using` = classOf[RequestStatusSerializer])
@JsonDeserialize(`using` = classOf[RequestStatusDeserializer])
enum RequestStatus(val desc: String):

  override def toString: String = this.desc

  case Pending extends RequestStatus("待处理") // 待处理
  case Approved extends RequestStatus("审核通过") // 审核通过
  case Rejected extends RequestStatus("审核拒绝") // 审核拒绝


object RequestStatus:
  given encode: Encoder[RequestStatus] = Encoder.encodeString.contramap[RequestStatus](toString)

  given decode: Decoder[RequestStatus] = Decoder.decodeString.emap(fromStringEither)

  def fromString(s: String):RequestStatus  = s match
    case "待处理" => Pending
    case "审核通过" => Approved
    case "审核拒绝" => Rejected
    case _ => throw Exception(s"Unknown RequestStatus: $s")

  def fromStringEither(s: String):Either[String, RequestStatus]  = s match
    case "待处理" => Right(Pending)
    case "审核通过" => Right(Approved)
    case "审核拒绝" => Right(Rejected)
    case _ => Left(s"Unknown RequestStatus: $s")

  def toString(t: RequestStatus): String = t match
    case Pending => "待处理"
    case Approved => "审核通过"
    case Rejected => "审核拒绝"


// Jackson 序列化器
class RequestStatusSerializer extends JsonSerializer[RequestStatus] {
  override def serialize(value: RequestStatus, gen: JsonGenerator, serializers: SerializerProvider): Unit = {
    gen.writeString(RequestStatus.toString(value)) // 直接写出字符串
  }
}

// Jackson 反序列化器
class RequestStatusDeserializer extends JsonDeserializer[RequestStatus] {
  override def deserialize(p: JsonParser, ctxt: DeserializationContext): RequestStatus = {
    RequestStatus.fromString(p.getText)
  }
}

