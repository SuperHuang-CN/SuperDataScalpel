package cn.superhuang.data.scalpel.business.task.web.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ModelQualityTaskDefinitionResponse(
        UUID taskId,
        boolean configured,
        Integer version,
        TaskModelReferenceResponse targetModel,
        int failureSampleLimit,
        long executableRuleCount,
        long skippedRuleCount,
        List<SkippedRule> skippedRules,
        Instant updatedAt
) {
    public ModelQualityTaskDefinitionResponse {
        skippedRules = skippedRules == null ? List.of() : List.copyOf(skippedRules);
    }

    public static ModelQualityTaskDefinitionResponse unconfigured(UUID taskId) {
        return new ModelQualityTaskDefinitionResponse(taskId, false, null, null, 100, 0, 0, List.of(), null);
    }

    public record SkippedRule(UUID ruleId, String ruleName, String reason) {
    }
}
