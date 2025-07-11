package APIs.CreatorService

import Common.API.API
import Global.ServiceCenter.CreatorServiceCode
import Objects.CreatorService.{CreatorID_Type, CreatorType} // 移除了 SearchCreatorResponse
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

/**
 * SearchCreatorByName
 * desc: 根据名称模糊搜索创作者（艺术家或乐队），支持按类型过滤和分页。
 *
 * @param userID      发起请求的用户ID。
 * @param userToken   用户的认证令牌。
 * @param name        要搜索的创作者名称。
 * @param creatorType 可选，用于过滤创作者类型 ("artist" 或 "band")。如果为None，则搜索所有类型。
 * @param pageNumber  页码，从1开始。
 * @param pageSize    每页大小。
 * @return (Option[(List[CreatorID_Type], Int)], String): 
 *         成功时为 (Some((结果列表, 总数)), 操作信息), 失败时为 (None, 错误信息)。
 */
case class SearchCreatorByName(
  userID: String,
  userToken: String,
  name: String,
  creatorType: Option[CreatorType],
  pageNumber: Int,
  pageSize: Int
) extends API[(Option[(List[CreatorID_Type], Int)], String)](CreatorServiceCode) // <-- 主要修改在这里

object SearchCreatorByName {
  // 确保 CreatorType 和 CreatorID_Type 的自定义编解码器在作用域内
  import Objects.CreatorService.CreatorType.given
  import Objects.CreatorService.CreatorID_Type.given

  // Circe 的标准编解码器，对于包含 Option 和自定义类型的 case class 同样适用
  // 注意：由于返回类型是元组，Circe 会自动处理，无需为元组提供显式编解码器。
  given Encoder[SearchCreatorByName] = deriveEncoder
  given Decoder[SearchCreatorByName] = deriveDecoder
}