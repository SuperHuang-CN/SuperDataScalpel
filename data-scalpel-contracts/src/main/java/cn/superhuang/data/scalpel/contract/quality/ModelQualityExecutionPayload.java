package cn.superhuang.data.scalpel.contract.quality;

import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Protected execution snapshot for one full-model quality inspection. */
public record ModelQualityExecutionPayload(
        QualityModelSnapshot targetModel,
        List<QualityRuleSnapshot> rules,
        List<SkippedQualityRuleSnapshot> skippedRules,
        List<QualityModelSnapshot> referenceModels,
        List<QualityDictionarySnapshot> dictionaries,
        int failureSampleLimit
) {
    public ModelQualityExecutionPayload {
        rules = rules == null ? List.of() : List.copyOf(rules);
        skippedRules = skippedRules == null ? List.of() : List.copyOf(skippedRules);
        referenceModels = referenceModels == null ? List.of() : List.copyOf(referenceModels);
        dictionaries = dictionaries == null ? List.of() : List.copyOf(dictionaries);
        if (failureSampleLimit < 0 || failureSampleLimit > 1000) {
            throw new IllegalArgumentException("质检失败样本数无效");
        }
    }

    public ModelQualityExecutionPayload(
            QualityModelSnapshot targetModel,
            List<QualityRuleSnapshot> rules,
            List<SkippedQualityRuleSnapshot> skippedRules,
            List<QualityModelSnapshot> referenceModels,
            List<QualityDictionarySnapshot> dictionaries
    ) {
        this(targetModel, rules, skippedRules, referenceModels, dictionaries, 0);
    }

    public record QualityModelSnapshot(
            UUID id,
            String code,
            String name,
            int schemaVersion,
            UUID dataSourceId,
            String catalogName,
            String schemaName,
            String physicalTableName,
            List<QualityFieldSnapshot> fields
    ) {
        public QualityModelSnapshot {
            fields = fields == null ? List.of() : List.copyOf(fields);
        }
    }

    public record QualityFieldSnapshot(
            UUID id,
            String code,
            String name,
            int sortOrder,
            boolean nullable,
            boolean primaryKey,
            PlatformTypeDefinition type,
            UUID dictionaryId
    ) {
        public QualityFieldSnapshot(
                UUID id, String code, String name, int sortOrder, boolean nullable,
                PlatformTypeDefinition type, UUID dictionaryId
        ) {
            this(id, code, name, sortOrder, nullable, false, type, dictionaryId);
        }
    }

    public record QualityRuleSnapshot(
            UUID id,
            String name,
            ModelQualityRuleType type,
            ModelQualityRuleSeverity severity,
            ModelQualityRuleDefinition definition
    ) {
    }

    public record SkippedQualityRuleSnapshot(
            UUID id,
            String name,
            ModelQualityRuleType type,
            String reasonCode,
            String reason
    ) {
    }

    public record QualityDictionarySnapshot(
            UUID id,
            boolean enabled,
            List<String> effectiveValues
    ) {
        public QualityDictionarySnapshot {
            effectiveValues = effectiveValues == null ? List.of() : List.copyOf(effectiveValues);
        }
    }
}
