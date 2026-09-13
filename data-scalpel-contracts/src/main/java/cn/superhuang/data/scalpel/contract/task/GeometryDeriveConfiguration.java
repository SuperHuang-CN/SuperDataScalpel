package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("逐行几何派生配置，支持批处理和流处理。保留来源表全部字段，并按 derivations 顺序追加 1 至 32 个 Geometry 字段；每项都只读取进入节点时的原始来源 Schema，不能引用同一节点刚生成的字段。NULL 来源产生 NULL 结果，不删除原行；输出表继承来源有界性、事件时间和 Watermark。")

public record GeometryDeriveConfiguration(
        @JsonPropertyDescription("要处理的上游 Canvas 逻辑表名，必须精确引用此前节点已经产生的可用输出表；其他上游逻辑表会继续传播但不参与本节点计算。")
        String sourceTableName,
        @JsonPropertyDescription("追加结果使用的 Canvas 逻辑表名，必须与当前所有上游表名不同；来源表及其他上游表仍然保留。")
        String outputTableName,
        @JsonPropertyDescription("有序派生项列表，必须包含 1 至 32 项且不能含 null；输出字段按此顺序追加。各项相互独立，只能读取来源表原有 Geometry 字段。")
        List<GeometryDerivation> derivations
) {
    public static final int MAX_DERIVATIONS = 32;

    public GeometryDeriveConfiguration {
        derivations = derivations == null ? null : List.copyOf(derivations);
    }
}
