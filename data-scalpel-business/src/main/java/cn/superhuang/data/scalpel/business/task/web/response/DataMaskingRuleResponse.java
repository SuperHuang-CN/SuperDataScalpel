package cn.superhuang.data.scalpel.business.task.web.response;

import cn.superhuang.data.scalpel.contract.task.MaskingRuleDefinition;
import cn.superhuang.data.scalpel.contract.task.MaskingStrategy;

import java.time.Instant;
import java.util.UUID;

public record DataMaskingRuleResponse(
        UUID id,
        String code,
        String name,
        String description,
        MaskingStrategy strategy,
        MaskingRuleDefinition definition,
        Instant createdAt,
        Instant updatedAt
) {
}
