package cn.superhuang.data.scalpel.business.quality.web.response;

import cn.superhuang.data.scalpel.business.quality.domain.ModelQualityRule;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityRuleDefinition;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityRuleSeverity;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityRuleType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ModelQualityRuleResponse(
        UUID id,
        UUID modelId,
        String name,
        String description,
        ModelQualityRuleType ruleType,
        ModelQualityRuleSeverity severity,
        boolean enabled,
        String invalidCode,
        String invalidReason,
        ModelQualityRuleDefinition definition,
        List<ModelQualityRuleFieldResponse> fields,
        ModelQualityRuleReferenceTargetResponse referenceTarget,
        Instant createdAt,
        Instant updatedAt
) {
    public static ModelQualityRuleResponse from(
            ModelQualityRule rule,
            ModelQualityRuleDefinition definition,
            List<ModelQualityRuleFieldResponse> fields,
            ModelQualityRuleReferenceTargetResponse referenceTarget
    ) {
        return new ModelQualityRuleResponse(
                rule.getId(), rule.getModelId(), rule.getName(), rule.getDescription(), rule.getRuleType(),
                rule.getSeverity(), rule.isEnabled(), rule.getInvalidCode(), rule.getInvalidReason(),
                definition, fields, referenceTarget, rule.getCreatedAt(), rule.getUpdatedAt()
        );
    }
}
