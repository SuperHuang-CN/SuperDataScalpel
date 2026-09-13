package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("Canvas 编译上下文中的一张逻辑表；包含来源、稳定字段顺序以及批数据或流数据的时间语义。")
public record CanvasTableSchema(
        @JsonPropertyDescription("Canvas 逻辑表名，在当前编译上下文中唯一；后续节点通过该值引用数据集。")
        String name,
        @JsonPropertyDescription("该逻辑表的输入来源定位信息；origin.kind 决定其余数据源、模型、文件表或主题字段如何解释。")
        CanvasTableOrigin origin,
        @JsonPropertyDescription("逻辑表字段 Schema，按稳定输出顺序排列。")
        List<CanvasColumnSchema> columns,
        @JsonPropertyDescription("数据集有界性：BOUNDED 为有限批数据，UNBOUNDED 为持续流数据。")
        CanvasDatasetKind datasetKind,
        @JsonPropertyDescription("UNBOUNDED 数据集的事件时间字段名；未声明事件时间时为空。")
        String eventTimeColumn,
        @JsonPropertyDescription("UNBOUNDED 数据集的 Watermark 延迟表达式；未启用 Watermark 时为空。")
        String watermarkDelay
) {
    public CanvasTableSchema {
        columns = columns == null ? List.of() : List.copyOf(columns);
        datasetKind = datasetKind == null ? CanvasDatasetKind.BOUNDED : datasetKind;
    }

    public CanvasTableSchema(String name, CanvasTableOrigin origin, List<CanvasColumnSchema> columns) {
        this(name, origin, columns, CanvasDatasetKind.BOUNDED, null, null);
    }
}
