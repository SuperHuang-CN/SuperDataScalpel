package cn.superhuang.data.scalpel.business.service.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "使用未保存的结构化样式和指定地图范围渲染一次 PNG 预览。")

public record RenderSpatialStylePreviewRequest(
        @Schema(description = "用于本次 PNG 预览的完整结构化制图样式；只渲染结果，不保存为服务样式。")
        @NotNull @Valid SpatialStyleDocument styleDocument,
        @Schema(description = "预览空间范围，依次为 minX、minY、maxX、maxY，固定使用 EPSG:3857 Web Mercator；四项必须为有限数字、最小值小于最大值且绝对值不超过 20037508.342789244。")
        @NotNull @Size(min = 4, max = 4) List<Double> bbox,
        @Schema(description = "输出 PNG 宽度，单位像素，范围 256～1600。")
        @Min(256) @Max(1600) int width,
        @Schema(description = "输出 PNG 高度，单位像素，范围 256～1200。")
        @Min(256) @Max(1200) int height
) {
}
