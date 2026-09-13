package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("批处理空间服务输入配置；从一个已启用且具有 SOURCE 用途的空间服务数据源读取一个或多个 ArcGIS REST/WFS 要素资源，每项产生一张带明确 EPSG、Geometry 类型和 XY 维度的 BOUNDED 表。")

public record SpatialServiceInputConfiguration(
        @JsonPropertyDescription("提供资源的空间服务数据源 UUID 字符串；草稿可为空，编译前必须解析为已启用、具有 SOURCE 用途且能提供受控 HTTP 连接的空间服务数据源。")
        String dataSourceId,
        @JsonPropertyDescription("按顺序读取的空间要素资源；至少一项，同一资源 UUID 不能重复且必须属于 dataSourceId。各输出表名必须唯一；任一资源无效会使整个节点失败。")
        List<SpatialServiceInputResourceSelection> resources
) {
    public SpatialServiceInputConfiguration {
        resources = resources == null ? List.of() : List.copyOf(resources);
    }
}
