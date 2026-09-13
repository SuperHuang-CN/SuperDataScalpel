package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonClassDescription("一对一空间连接从右侧匹配记录中确定性保留一条的规则。平台不依赖 Spark 输入顺序；完整排序并列会在真实执行时失败。")
public record SpatialJoinKeepRule(
        @JsonPropertyDescription("必填保留策略。FIRST 只使用 stableOrder；其他策略先按 orderByColumnName 的官方方向排序。")
        SpatialJoinKeepStrategy strategy,
        @JsonPropertyDescription("LARGEST/SMALLEST 必填数值字段，NEWEST/OLDEST 必填 DATE/TIMESTAMP/TIMESTAMP_NTZ 字段；FIRST 必须为空。")
        String orderByColumnName,
        @JsonPropertyDescription("必填且至少一项的右侧稳定排序字段。FIRST 时定义完整首项顺序；其他策略用于主值并列时继续排序。字段可配置方向和 NULL 位置，不能使用 Geometry。完整排序仍并列时运行失败，不任意选择。")
        List<SortField> stableOrder
) {
    public SpatialJoinKeepRule {
        stableOrder = stableOrder == null ? null : List.copyOf(stableOrder);
    }
}
