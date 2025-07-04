package Global

object ServiceCenter {
  val projectName: String = "PlusUltraMDB"
  val dbManagerServiceCode = "A000001"
  val tongWenDBServiceCode = "A000002"
  val tongWenServiceCode = "A000003"

  val MusicServiceCode = "A000010"
  val OrganizeServiceCode = "A000011"
  val CreatorServiceCode = "A000012"
  val StatisticsServiceCode = "A000013"

  val fullNameMap: Map[String, String] = Map(
    tongWenDBServiceCode -> "DB-Manager（DB-Manager）",
    tongWenServiceCode -> "Tong-Wen（Tong-Wen）",
    MusicServiceCode -> "MusicService（MusicService)",
    OrganizeServiceCode -> "OrganizeService（OrganizeService)",
    CreatorServiceCode -> "CreatorService（CreatorService)",
    StatisticsServiceCode -> "TrackService（TrackService)"
  )

  def serviceName(serviceCode: String): String = {
    fullNameMap(serviceCode).toLowerCase
  }
}
