package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("有界批处理空间叠加配置；按要素执行相交、擦除、联合、标识或对称差，并显式投影左右属性。结果追加为不带事件时间和 Watermark 的有界表；不保证行顺序，也不把同侧重叠要素整理成全局无重叠分区。")

public record SpatialOverlayConfiguration(
        @JsonPropertyDescription("必填的左侧有界输入逻辑表名；必须引用上游表，且不能与 rightTableName 相同。左侧决定 ERASE、IDENTITY 及 FAMILY_2D 非相交操作的结果几何家族。")
        String leftTableName,
        @JsonPropertyDescription("必填的左侧 Geometry 字段名；字段必须带完整几何类型、CRS 和坐标维度元数据。")
        String leftGeometryColumnName,
        @JsonPropertyDescription("必填的右侧有界输入逻辑表名；必须引用上游表，且不能与 leftTableName 相同。")
        String rightTableName,
        @JsonPropertyDescription("必填的右侧 Geometry 字段名；字段必须带完整几何元数据，并与左侧使用相同 CRS 和坐标维度，系统不会隐式重投影。")
        String rightGeometryColumnName,
        @JsonPropertyDescription("必填的叠加运算：INTERSECTION 输出成对交叠；ERASE 输出左侧减去全部相交右侧遮罩；UNION 输出成对交叠及双方独有部分；IDENTITY 输出成对交叠及左侧独有部分；SYMMETRICAL_DIFFERENCE 只输出双方独有部分。")
        SpatialOverlayOperation operation,
        @JsonPropertyDescription("当前操作产生的 Canvas 逻辑表名；必须在任务定义内唯一，后续节点通过该值引用结果。")
        String outputTableName,
        @JsonPropertyDescription("必填且不能与投影字段重名的结果 Geometry 字段名；NULL 或 Empty 结果不会生成记录。")
        String outputGeometryColumnName,
        @JsonPropertyDescription("必填的左右来源字段投影，至少启用一项；同一来源字段只能配置一次，启用项的输出名必须唯一。ERASE 不允许启用右侧字段；IDENTITY 左侧独有记录的右字段为 NULL；UNION 和 SYMMETRICAL_DIFFERENCE 中任一缺失侧字段为 NULL。直接投影的来源 Geometry 不受 geometryPolicy 改写。")
        List<JoinOutputColumn> outputColumns,
        @JsonPropertyDescription("输出几何策略：FAMILY_2D 校验点/线/面组合、强制 XY、只保留声明家族并输出对应 Multi*；LEGACY_GEOMETRY 保留通用 Geometry 兼容语义且只适用于 INTERSECTION、ERASE、UNION。null 对旧三种操作使用兼容语义，对 IDENTITY、SYMMETRICAL_DIFFERENCE 自动使用 FAMILY_2D。显式策略或后两种操作要求 Canvas 4.26 及以上。")
        SpatialOverlayGeometryPolicy geometryPolicy
) {
    public SpatialOverlayConfiguration {
        outputColumns = outputColumns == null ? null : List.copyOf(outputColumns);
    }

    public SpatialOverlayConfiguration(String leftTableName, String leftGeometryColumnName,
            String rightTableName, String rightGeometryColumnName, SpatialOverlayOperation operation,
            String outputTableName, String outputGeometryColumnName, List<JoinOutputColumn> outputColumns) {
        this(leftTableName, leftGeometryColumnName, rightTableName, rightGeometryColumnName,
                operation, outputTableName, outputGeometryColumnName, outputColumns, null);
    }

    public boolean usesFamilyGeometry() {
        return geometryPolicy == SpatialOverlayGeometryPolicy.FAMILY_2D
                || operation == SpatialOverlayOperation.IDENTITY
                || operation == SpatialOverlayOperation.SYMMETRICAL_DIFFERENCE;
    }

    public boolean requiresFamilyGeometryVersion() {
        return geometryPolicy != null || usesFamilyGeometry();
    }
}
