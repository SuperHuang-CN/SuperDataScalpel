package cn.superhuang.data.scalpel.business.panorama.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import java.time.LocalDateTime;
@Schema(description = "地图展示所需的一条全景位置、拍摄时间和内容版本摘要。")
public record PanoramaMapPointResponse(
        @Schema(description = "全景影像 UUID。")
        UUID id,
        @Schema(description = "全景影像显示名称。")
        String name,
        @Schema(description = "WGS84 纬度，范围 -90 到 90。")
        Double latitude,
        @Schema(description = "WGS84 经度，范围 -180 到 180。")
        Double longitude,
        @Schema(description = "全景有效拍摄本地时间，不含 UTC 偏移；无法确定时为空。地图摘要不返回 captureOffset，需要时查询详情。")
        LocalDateTime captureTime,
        @Schema(description = "当前成品版本号，可作为后续缩略图、预览图或原图请求的 version。")
        long contentVersion
) {}
