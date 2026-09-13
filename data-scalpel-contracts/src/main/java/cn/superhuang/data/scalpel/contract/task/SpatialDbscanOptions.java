package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
/** Explicit density-connected semantics; absent options retain the legacy spatial implementation. */
@JsonClassDescription("Canvas 4.40 DBSCAN 附加语义；LEGACY_SPATIAL 使用旧 Sedona 空间路径，SPATIAL 使用平台的密度连通实现，LINEAR 在同一空间邻域上再要求绝对时间差不超过指定时长。")
public record SpatialDbscanOptions(
        @JsonPropertyDescription("必填的 DBSCAN 模式：LEGACY_SPATIAL 保持旧版空间实现；SPATIAL 使用显式密度连通规则；LINEAR 同时要求空间距离和时间差命中。NULL 是无效草稿，不等同于整个 dbscan 对象为 NULL。")
        Mode mode,
        @JsonPropertyDescription("LINEAR 模式必填的 TIMESTAMP 来源字段名；该字段为 NULL 的记录不参与聚类。其他模式忽略并保留该草稿值。")
        String timeColumnName,
        @JsonPropertyDescription("LINEAR 模式必填的正整数最大绝对时间差；与 searchDurationUnit 一起换算后必须落在微秒 Long 范围内。其他模式忽略。")
        Long searchDuration,
        @JsonPropertyDescription("LINEAR 模式必填的固定时长单位；与 searchDuration 共同定义时间邻域，其他模式忽略。WEEKS 要求 Canvas 4.47 或更高版本。")
        SpatialDurationUnit searchDurationUnit
) {
    @JsonClassDescription("DBSCAN 执行模式：LEGACY_SPATIAL 为旧空间实现；SPATIAL 为平台空间密度连通；LINEAR 在空间邻域基础上增加对称的绝对时间差约束，不按时间桶分别聚类。")
    public enum Mode { LEGACY_SPATIAL, SPATIAL, LINEAR }
    public boolean usesTime() { return mode == Mode.LINEAR; }
}
