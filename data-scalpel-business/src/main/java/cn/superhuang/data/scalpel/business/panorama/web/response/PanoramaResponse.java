package cn.superhuang.data.scalpel.business.panorama.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import java.time.Instant;
import java.time.LocalDateTime;
import cn.superhuang.data.scalpel.business.panorama.domain.*;
@Schema(description = "全景资源详情，包含治理信息、当前成品、候选内容及其异步处理状态。")
public record PanoramaResponse(
        @Schema(description = "全景资源 UUID，用于更新治理信息、上传候选内容和下载成品。")
        UUID id,
        @Schema(description = "全景资源显示名称。")
        String name,
        @Schema(description = "用途说明；未填写时为空。")
        String description,
        @Schema(description = "所属目录 UUID；位于根目录时为空。")
        UUID directoryId,
        @Schema(description = "当前有效的照片本地拍摄时间，不含时区；AUTO 来自当前成品元数据，MANUAL 来自用户维护，无法确定或明确清空时为空。")
        LocalDateTime captureTime,
        @Schema(description = "captureTime 相对 UTC 的 ZoneOffset，例如 +08:00 或 Z；时间缺少时区、captureTime 为空或人工未填写时为空。")
        String captureOffset,
        @Schema(description = "当前有效的 WGS84 纬度，范围 -90 到 90；AUTO 来自可信文件元数据，MANUAL 来自用户维护，没有位置时为空并且 longitude 同时为空。")
        Double latitude,
        @Schema(description = "当前有效的 WGS84 经度，范围 -180 到 180；没有位置时为空并且 latitude 同时为空。")
        Double longitude,
        @Schema(description = "时间取值方式：AUTO 来自当前文件元数据，MANUAL 由用户维护")
        PanoramaValueMode timeMode,
        @Schema(description = "位置取值方式：AUTO 来自当前文件元数据，MANUAL 由用户维护")
        PanoramaValueMode locationMode,
        @Schema(description = "当前成品版本号；首次候选成功成为成品及每次替换成功时递增。上传候选、处理失败和修改治理资料不会递增。")
        long contentVersion,
        @Schema(description = "内容处理状态。QUEUED、PROCESSING、FAILED 描述 candidateContent；READY 表示没有候选且 currentContent 可用。替换失败时状态为 FAILED，但旧 currentContent 仍可使用。")
        PanoramaProcessingStatus processingStatus,
        @Schema(description = "候选处理失败的安全错误摘要；processingStatus 不是 FAILED 时为空。")
        String processingError,
        @Schema(description = "当前可预览和下载的成品内容；首次上传仍在排队、处理中或失败时为空。替换候选处理期间仍返回旧成品。")
        PanoramaContentResponse currentContent,
        @Schema(description = "正在排队、处理或处理失败的候选内容；首次上传也先作为候选。处理成功后转为 currentContent 并清空此字段。")
        PanoramaContentResponse candidateContent,
        @Schema(description = "创建时间，ISO-8601 UTC 时间戳。")
        Instant createdAt,
        @Schema(description = "最后更新时间，ISO-8601 UTC 时间戳。")
        Instant updatedAt
) {}
