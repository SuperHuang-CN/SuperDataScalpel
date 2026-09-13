package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("逐行空间量测配置，支持批处理和流处理。保留来源表全部字段，并按 measurements 顺序追加 1 至 32 个 nullable DOUBLE 字段；各项只读取进入节点时的原始 Geometry 字段且彼此独立。节点不隐式转换 CRS；Canvas 4.50 可为面积、长度、周长和距离显式选择输出单位。")

public record SpatialMeasureConfiguration(
        @JsonPropertyDescription("要量测的上游 Canvas 逻辑表名，必须精确引用此前节点已经产生的可用输出表；其他上游表继续传播但不参与量测。")
        String sourceTableName,
        @JsonPropertyDescription("追加量测结果使用的 Canvas 逻辑表名，必须与当前所有上游表名不同；来源表及其字段保持不变。")
        String outputTableName,
        @JsonPropertyDescription("有序量测项列表，必须包含 1 至 32 项且不能含 null；输出字段按数组顺序追加。AREA、LENGTH、PERIMETER、DISTANCE 需要 mode，X/Y 不使用 mode；任何所需 Geometry 为 NULL 时仅对应结果为 NULL。")
        List<SpatialMeasurement> measurements
) {
    public static final int MAX_MEASUREMENTS = 32;

    public SpatialMeasureConfiguration {
        measurements = measurements == null ? null : List.copyOf(measurements);
    }
}
