package cn.superhuang.data.scalpel.contract.quality;

public record QualitySummary(
        QualityConclusion conclusion,
        long totalRules,
        long passedRules,
        long failedRules,
        long skippedRules,
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
