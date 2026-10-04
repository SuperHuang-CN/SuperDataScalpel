package cn.superhuang.data.scalpel.business.spatialpreview.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "空间预览准备状态；完整读取、概览生成及版本校验完成后即可浏览，同代次副本保存完成后为 READY")
public record SpatialPreviewStatusResponse(
        @Schema(description = "NOT_PREPARED 未准备、PREPARING 准备中、OVERVIEW_READY 完整概览可浏览（副本保存中或未完成，其他节点暂回退概览）、READY 副本已就绪、UPDATING 来源正在写入、FAILED 可重试失败、LIMIT_EXCEEDED 超预算、UNSUPPORTED 不支持") String state,
        @Schema(description = "当前准备代次 UUID；OVERVIEW_READY 和 READY 可读取图片，请携带此代次防止过期响应；两状态间转换不更换代次") UUID generation,
        @Schema(description = "当前阶段或失败原因；不包含连接凭据") String message,
        @Schema(description = "完整副本要素数；准备未完成时为 0，空几何不计入") long featureCount,
        @Schema(description = "跳过的空几何数量；损坏几何使准备失败，不静默丢弃") long emptyCount,
        @Schema(description = "完整数据的 WGS84 范围 [west,south,east,north]；未就绪或空表时为空数组") List<Double> bounds,
        @Schema(description = "完整概览的 EPSG:3857 范围 [minX,minY,maxX,maxY]；与 PNG 定位一致") List<Double> imageBounds,
        @Schema(description = "本代次实际开始读取来源的时间；不是最近请求时间") Instant observedAt,
        @Schema(description = "数据新鲜期截止时间，到期需要受控重读；未就绪时为空") Instant expiresAt
) { }
