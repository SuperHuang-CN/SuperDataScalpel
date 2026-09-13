package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
/** One feature resource selected from a spatial service source. */
@JsonClassDescription("空间服务输入节点选择的一个已纳管要素资源；实际请求使用资源定义的 ArcGIS Query 或 WFS GetFeature 分页规则，Canvas 不保存服务 URL、请求参数、Schema 或凭据。")
public record SpatialServiceInputResourceSelection(
        @JsonPropertyDescription("空间要素资源 UUID 字符串；草稿可为空，编译前必须解析为 dataSourceId 下已启用且具有受支持 Geometry Schema 的资源。")
        String resourceId,
        @JsonPropertyDescription("该资源生成的 BOUNDED Canvas 逻辑表名；必须非空，并与本节点其他资源的输出表名不同。")
        String outputTableName
) {
}
