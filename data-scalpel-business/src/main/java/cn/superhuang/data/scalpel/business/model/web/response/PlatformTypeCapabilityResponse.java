package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "指定目标数据存储对一个平台字段类型及参数的支持能力")
public record PlatformTypeCapabilityResponse(
        @Schema(description = "平台数据类型") PlatformDataType type,
        @Schema(description = "目标方言能否无损映射该平台类型") boolean supported,
        @Schema(description = "能力说明或不支持原因") String message,
        @Schema(description = "该类型是否接受 length 参数") boolean lengthParameterSupported,
        @Schema(description = "字符串类型是否允许不指定长度以使用无界文本") boolean unboundedStringSupported,
        @Schema(description = "目标方言支持的 Geometry 子类型；非 Geometry 或不支持时为空列表") List<GeometryKind> geometryKinds,
        @Schema(description = "目标方言支持的坐标维度；非 Geometry 或不支持时为空列表") List<CoordinateDimension> coordinateDimensions,
        @Schema(description = "目标方言支持的 CRS authority，当前受管空间字段通常只允许 EPSG") List<String> crsAuthorities
) {
    public PlatformTypeCapabilityResponse {
        geometryKinds = geometryKinds == null ? List.of() : List.copyOf(geometryKinds);
        coordinateDimensions = coordinateDimensions == null ? List.of() : List.copyOf(coordinateDimensions);
        crsAuthorities = crsAuthorities == null ? List.of() : List.copyOf(crsAuthorities);
    }
}
