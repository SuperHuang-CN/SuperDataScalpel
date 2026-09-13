package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("空间分组聚合方法。UNION 拓扑融合组内覆盖范围，Empty 为恒等输入；INTERSECTION 求所有 Geometry 的公共部分，任一 Empty 会使结果 Empty；COLLECT 收集 Geometry 而不融合，Empty 仍作为成员交给 Sedona/JTS；ENVELOPE 求全部非空 Geometry 的总包络并忽略 Empty。四者都忽略 NULL，全部为 NULL 时返回 NULL；ENVELOPE 组内仅 NULL/Empty 时也返回 NULL。")
public enum SpatialAggregationKind {
    UNION,
    INTERSECTION,
    COLLECT,
    ENVELOPE
}
