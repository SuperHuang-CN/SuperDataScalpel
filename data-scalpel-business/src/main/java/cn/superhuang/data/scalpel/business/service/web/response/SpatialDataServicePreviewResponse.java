package cn.superhuang.data.scalpel.business.service.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "空间数据服务地图预览所需的图层、坐标系、初始视野和尺寸限制。")

public record SpatialDataServicePreviewResponse(
        @Schema(description = "当前服务是否已具备地图预览条件。")
        boolean available,
        @Schema(description = "地图预览不可用或边界暂不可取得时的可读原因；预览完全可用时为空。")
        String message,
        @Schema(description = "服务引擎中包含工作区前缀的完整图层名；available=false 时可能为空。")
        String qualifiedLayerName,
        @Schema(description = "initialBounds 固定使用的坐标参考系标识，当前为 EPSG:4326；地图渲染接口的 bbox 则固定使用 EPSG:3857。")
        String displayCrs,
        @Schema(description = "GeoServer latLonBoundingBox 生成的地图初始边界，固定顺序为 [west, south, east, north]，单位为 EPSG:4326 经纬度；退化点范围会向两侧最多补 0.01 度，无法取得有效边界时为空列表。")
        List<Double> initialBounds,
        @Schema(description = "空间预览允许的宽高范围。")
        Limits limits
) {
    public SpatialDataServicePreviewResponse {
        initialBounds = initialBounds == null ? List.of() : List.copyOf(initialBounds);
    }

    @Schema(description = "空间服务预览图允许的像素尺寸范围。")

    public record Limits(
            @Schema(description = "允许的最小预览宽度，单位像素。")
            int minimumWidth,
            @Schema(description = "允许的最大预览宽度，单位像素。")
            int maximumWidth,
            @Schema(description = "允许的最小预览高度，单位像素。")
            int minimumHeight,
            @Schema(description = "允许的最大预览高度，单位像素。")
            int maximumHeight
    ) {
    }
}
