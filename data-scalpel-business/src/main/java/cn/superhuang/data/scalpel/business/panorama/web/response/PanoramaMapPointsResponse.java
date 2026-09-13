package cn.superhuang.data.scalpel.business.panorama.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
@Schema(description = "地图当前查询范围内的全景点位轻量列表及截断信息。")
public record PanoramaMapPointsResponse(
        @Schema(description = "可在地图上展示的全景点位；不包含图片内容和完整详情。")
        List<PanoramaMapPointResponse> points,
        @Schema(description = "符合 Search DSL、目录、可选包围盒，且已有当前成品和有效经纬度的总记录数，可能大于 points 长度。")
        long totalElements,
        @Schema(description = "totalElements 是否大于固定返回上限 500；仅由数量上限决定。")
        boolean truncated
) {}
