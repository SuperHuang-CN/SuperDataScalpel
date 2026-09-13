package cn.superhuang.data.scalpel.business.datasource.web.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "将 ArcGIS 图层或 WFS Feature Type 登记为可复用的空间要素资源")
public record CreateSpatialFeatureResourceRequest(
        @Schema(description = "数据源内唯一资源编码，保存时去除首尾空白并转为小写；创建后不可修改", example = "administrative_boundary")
        @NotBlank @Size(max = 64) String code,
        @Schema(description = "资源显示名称")
        @NotBlank @Size(max = 100) String name,
        @Schema(description = "发现目录返回的远端图层标识；ArcGIS 通常为图层 URL 或 ID，WFS 通常为带命名空间的 Feature Type QName")
        @NotBlank @Size(max = 1000) String remoteIdentifier,
        @Schema(description = "期望输出坐标系的 EPSG 编码，范围 1 到 99999999。ArcGIS 省略时使用服务声明的坐标系，服务也无法解析时拒绝含几何的图层；WFS 解析几何字段需要明确提供可用 EPSG")
        @Min(1) @Max(99_999_999) Integer outputEpsgCode
) {
}
