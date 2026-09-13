package cn.superhuang.data.scalpel.contract.quality;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("一次技术成功的全量模型质检汇总；至少有一条规则实际执行，停用、失效或依赖不可用的规则仍以 skippedRules 计入快照总数。")
public record QualitySummary(
        @JsonPropertyDescription("质量结论：PASSED 表示 failedRules 为 0，FAILED 表示至少一条规则失败；它不代表执行是否发生技术错误。")
        QualityConclusion conclusion,
        @JsonPropertyDescription("本次规则快照总数，至少为 1，且等于 passedRules、failedRules 和 skippedRules 之和；passedRules 与 failedRules 之和也至少为 1。")
        long totalRules,
        @JsonPropertyDescription("执行后指标未超过阈值的规则数量。")
        long passedRules,
        @JsonPropertyDescription("执行后指标超过阈值的规则数量。")
        long failedRules,
        @JsonPropertyDescription("因停用、失效或依赖不可用而未执行的规则数量。")
        long skippedRules,
        @JsonPropertyDescription("目标模型在该次全量质检中读取的总行数，也是行级违规比例的分母。")
        long checkedRows
) {
    public QualitySummary {
        if (conclusion == null || totalRules < 1 || passedRules < 0 || failedRules < 0
                || skippedRules < 0 || checkedRows < 0
                || totalRules != passedRules + failedRules + skippedRules
                || conclusion == QualityConclusion.PASSED && failedRules != 0
                || conclusion == QualityConclusion.FAILED && failedRules == 0
                || passedRules + failedRules == 0) {
            throw new IllegalArgumentException("质量汇总无效");
        }
    }
}
