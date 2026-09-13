package cn.superhuang.data.scalpel.business.panorama.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.UUID;
import cn.superhuang.data.scalpel.business.panorama.domain.PanoramaValueMode;
@Schema(description = "整体修改全景名称、说明、目录、拍摄时间和位置。时间与位置分别选择自动或人工模式；未提供的可空人工值会被清空。")
public record UpdatePanoramaRequest(
        @Schema(description = "全景资源显示名称。")
        @NotBlank @Size(max = 255) String name,
        @Schema(description = "用途说明；未填写时为空。")
        @Size(max = 10000) String description,
        @Schema(description = "所属目录 UUID；位于根目录时为空。")
        UUID directoryId,
        @Schema(description = "从最新全景详情读取的 contentVersion；仅用于确认当前图片内容未被替换，不检测治理字段之间的并发修改。")
        @NotNull @Min(0) Long expectedContentVersion,
        @Schema(description = "时间取值方式。AUTO 忽略请求中的时间值并重新采用当前成品元数据，无法提取时清空；MANUAL 使用 captureTime 和可选 captureOffset。")
        @NotNull PanoramaValueMode timeMode,
        @Schema(description = "人工维护的照片本地拍摄时间，不含时区；MANUAL 下允许为空，AUTO 下提交值最终会被当前成品元数据覆盖。")
        LocalDateTime captureTime,
        @Schema(description = "拍摄时间相对 UTC 的 ZoneOffset，例如 +08:00 或 Z；空白按空值保存，captureTime 为空时强制清空。AUTO 下仍需格式合法，但最终采用当前成品元数据。")
        @Size(max = 10) String captureOffset,
        @Schema(description = "位置取值方式。AUTO 忽略请求坐标并重新采用当前成品中可信的 WGS84 坐标，无法提取时清空；MANUAL 使用 latitude 和 longitude。")
        @NotNull PanoramaValueMode locationMode,
        @Schema(description = "人工维护的 WGS84 纬度，范围 -90 到 90；必须与 longitude 同时填写或同时清空。AUTO 下提交值最终会被当前成品元数据覆盖。")
        @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @Schema(description = "人工维护的 WGS84 经度，范围 -180 到 180；必须与 latitude 同时填写或同时清空。AUTO 下提交值最终会被当前成品元数据覆盖。")
        @DecimalMin("-180") @DecimalMax("180") Double longitude
) {}
