package cn.superhuang.data.scalpel.contract.quality;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonClassDescription("一次全量模型质检提交给 Runner 的受保护执行快照；包含目标模型、可执行规则、跳过规则、引用模型、字典有效值和失败样本上限，创建运行后不再回查这些业务定义。")
public record ModelQualityExecutionPayload(
        @JsonPropertyDescription("本次全量质检读取的目标模型不可变快照。")
        QualityModelSnapshot targetModel,
        @JsonPropertyDescription("本次实际执行的启用且依赖可用的规则快照；至少包含一条，规则变更不会影响已创建运行。")
        List<QualityRuleSnapshot> rules,
        @JsonPropertyDescription("因停用、定义失效或引用依赖不可用而不执行的规则快照；仍计入质量汇总 totalRules 和 skippedRules。")
        List<SkippedQualityRuleSnapshot> skippedRules,
        @JsonPropertyDescription("REFERENCE_EXISTS 规则需要读取的目标模型快照；不重复包含 targetModel 自身。")
        List<QualityModelSnapshot> referenceModels,
        @JsonPropertyDescription("DICTIONARY_MEMBERSHIP 规则需要的标准字典有效值快照。")
        List<QualityDictionarySnapshot> dictionaries,
        @JsonPropertyDescription("每条失败行级规则最多保存的样本行数，范围 0 到 1000；0 表示禁用失败样本。")
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

    @JsonClassDescription("运行创建时固化的一个目标或引用模型物理读取契约。")
    public record QualityModelSnapshot(
            @JsonPropertyDescription("运行创建时固化的模型 UUID。")
            UUID id,
            @JsonPropertyDescription("运行创建时固化的模型编码。")
            String code,
            @JsonPropertyDescription("运行创建时固化的模型名称。")
            String name,
            @JsonPropertyDescription("运行创建时固化的模型 Schema 版本。")
            int schemaVersion,
            @JsonPropertyDescription("模型物理表所在数据源 UUID。")
            UUID dataSourceId,
            @JsonPropertyDescription("物理表 Catalog 名；数据库不支持或未配置 Catalog 时为空。")
            String catalogName,
            @JsonPropertyDescription("物理表 Schema 名；数据库不支持或未配置 Schema 时为空。")
            String schemaName,
            @JsonPropertyDescription("质检实际全量读取的物理表名。")
            String physicalTableName,
            @JsonPropertyDescription("运行创建时固化的模型字段，按模型字段顺序排列。")
            List<QualityFieldSnapshot> fields
    ) {
        public QualityModelSnapshot {
            fields = fields == null ? List.of() : List.copyOf(fields);
        }
    }

    @JsonClassDescription("运行创建时固化的一个模型字段及其完整平台类型。")
    public record QualityFieldSnapshot(
            @JsonPropertyDescription("模型字段 UUID，供规则定义稳定引用。")
            UUID id,
            @JsonPropertyDescription("模型字段编码，也是读取后的 Spark 列名。")
            String code,
            @JsonPropertyDescription("模型字段展示名称。")
            String name,
            @JsonPropertyDescription("模型字段显示和投影顺序。")
            int sortOrder,
            @JsonPropertyDescription("模型定义是否允许该字段为 NULL；仅作为快照元数据，具体非空检查由规则决定。")
            boolean nullable,
            @JsonPropertyDescription("是否为模型主键组成字段；安全类型的完整主键会随失败样本保存以支持行定位。")
            boolean primaryKey,
            @JsonPropertyDescription("完整平台类型定义，用于构造读取 Schema 和解析规则常量。")
            PlatformTypeDefinition type,
            @JsonPropertyDescription("运行创建时字段绑定的标准字典 UUID；未绑定时为空。")
            UUID dictionaryId
    ) {
        public QualityFieldSnapshot(
                UUID id, String code, String name, int sortOrder, boolean nullable,
                PlatformTypeDefinition type, UUID dictionaryId
        ) {
            this(id, code, name, sortOrder, nullable, false, type, dictionaryId);
        }
    }

    @JsonClassDescription("本次运行实际执行的一条启用规则快照。Runner 按严重程度枚举顺序和规则名称排序执行，单条规则发生技术错误会终止整个质检。")
    public record QualityRuleSnapshot(
            @JsonPropertyDescription("质量规则 UUID。")
            UUID id,
            @JsonPropertyDescription("运行创建时固化的规则名称。")
            String name,
            @JsonPropertyDescription("运行创建时固化的规则类型。")
            ModelQualityRuleType type,
            @JsonPropertyDescription("运行创建时固化的规则严重程度。")
            ModelQualityRuleSeverity severity,
            @JsonPropertyDescription("本次执行使用的完整多态规则定义快照。")
            ModelQualityRuleDefinition definition
    ) {
    }

    @JsonClassDescription("运行创建时已确定不会执行的一条规则；它仍进入结果汇总，但不携带严重程度和指标。")
    public record SkippedQualityRuleSnapshot(
            @JsonPropertyDescription("被跳过的质量规则 UUID。")
            UUID id,
            @JsonPropertyDescription("运行创建时固化的规则名称。")
            String name,
            @JsonPropertyDescription("运行创建时固化的规则类型。")
            ModelQualityRuleType type,
            @JsonPropertyDescription("稳定跳过原因码，例如 RULE_DISABLED、FIELD_MISSING、DICTIONARY_DISABLED 或 REFERENCE_DEPENDENCY_UNAVAILABLE。")
            String reasonCode,
            @JsonPropertyDescription("面向用户的跳过原因。")
            String reason
    ) {
    }

    @JsonClassDescription("DICTIONARY_MEMBERSHIP 使用的标准字典快照；effectiveValues 只包含自身启用且整条祖先链均存在并启用的去重条目编码。")
    public record QualityDictionarySnapshot(
            @JsonPropertyDescription("标准字典 UUID。")
            UUID id,
            @JsonPropertyDescription("运行创建时字典是否启用；可执行规则要求为 true。")
            boolean enabled,
            @JsonPropertyDescription("运行创建时固化的去重有效字典值；DICTIONARY_MEMBERSHIP 只接受这些值。")
            List<String> effectiveValues
    ) {
        public QualityDictionarySnapshot {
            effectiveValues = effectiveValues == null ? List.of() : List.copyOf(effectiveValues);
        }
    }
}
