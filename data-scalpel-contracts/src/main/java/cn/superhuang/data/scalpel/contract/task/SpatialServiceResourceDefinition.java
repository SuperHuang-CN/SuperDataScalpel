package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import java.util.List;
import java.util.UUID;

/** Stable, credential-free resource snapshot used by a spatial-service Canvas input. */
@JsonClassDescription("空间服务 Canvas 输入使用的无凭据资源快照；固定远端协议、资源标识、轴顺序和字段 Schema，不包含服务 URL 或认证秘密。")
public record SpatialServiceResourceDefinition(
        @JsonPropertyDescription("空间要素资源 UUID。")
        UUID id,
        @JsonPropertyDescription("空间要素资源在所属数据源内的稳定编码。")
        String code,
        @JsonPropertyDescription("空间要素资源显示名称。")
        String name,
        @JsonPropertyDescription("编译快照生成时该空间服务资源是否处于启用状态。")
        boolean enabled,
        @JsonPropertyDescription("远端空间服务协议：ARCGIS_REST 或 WFS。")
        SpatialServiceProtocol protocol,
        @JsonPropertyDescription("远端空间服务中的图层或要素类型标识。")
        String remoteIdentifier,
        @JsonPropertyDescription("远端空间要素 Geometry 字段名。")
        String geometryFieldName,
        @JsonPropertyDescription("坐标参考系 EPSG 正整数编码。")
        Integer epsgCode,
        @JsonPropertyDescription("远端空间要素稳定对象 ID 字段名。")
        String objectIdFieldName,
        @JsonPropertyDescription("WFS 协议版本。")
        String wfsVersion,
        @JsonPropertyDescription("远端空间服务响应格式。")
        String outputFormat,
        @JsonPropertyDescription("远端协议的坐标轴顺序，例如 XY 或 YX；用于解释和构造请求坐标。")
        String axisOrder,
        @JsonPropertyDescription("远端空间要素的输出字段 Schema，按服务返回顺序排列。")
        List<CanvasColumnSchema> columns
) {
    public SpatialServiceResourceDefinition {
        columns = columns == null ? List.of() : List.copyOf(columns);
    }
}
