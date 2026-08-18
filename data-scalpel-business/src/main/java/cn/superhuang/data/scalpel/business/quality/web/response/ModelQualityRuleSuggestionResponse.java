package cn.superhuang.data.scalpel.business.quality.web.response;

import cn.superhuang.data.scalpel.contract.quality.ModelQualityRuleDefinition;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityRuleSeverity;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityRuleType;

import java.util.List;

public record ModelQualityRuleSuggestionResponse(
        String key,
        String name,
        String reason,
        ModelQualityRuleType ruleType,
        ModelQualityRuleSeverity severity,
        ModelQualityRuleDefinition definition,
        List<ModelQualityRuleFieldResponse> fields
) {
}
