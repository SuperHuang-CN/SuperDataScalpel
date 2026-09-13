package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("快照同步处理目标独有行的策略。KEEP 必须同时令两个阈值为 NULL；DELETE 必须同时提供正整数最大删除行数和位于 (0,1] 的最大删除比例。候选删除数或其占目标总行数的比例大于阈值时整次失败，等于阈值允许；来源为空且目标非空时无论阈值如何都禁止删除。")
public record SnapshotDeletePolicy(
        @JsonPropertyDescription("目标中存在而来源完整快照不存在的记录处理方式；KEEP 保留并计入 retainedTargetOnlyRows，DELETE 才执行删除保护检查。")
        SnapshotTargetOnlyAction action,
        @JsonPropertyDescription("DELETE 必填的一次同步最大候选删除行数，必须 >= 1；候选数等于该值时允许。KEEP 时必须为 NULL。")
        Long maxDeleteRows,
        @JsonPropertyDescription("DELETE 必填的最大候选删除比例，取值 (0,1]，按候选删除行数/同步前目标总行数计算；等于该值时允许。KEEP 时必须为 NULL。")
        Double maxDeleteRatio
) {
}
