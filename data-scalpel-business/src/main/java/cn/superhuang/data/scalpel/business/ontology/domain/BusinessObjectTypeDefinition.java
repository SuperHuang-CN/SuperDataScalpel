package cn.superhuang.data.scalpel.business.ontology.domain;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Current, directly effective business object modelling definition. */
@Schema(description = "业务对象类型的当前建模定义。保存后立即生效，不维护草稿、发布版本或历史快照。")
public record BusinessObjectTypeDefinition(
        @Schema(description = "决定对象集合、唯一标识和展示名称的主来源；可为空以支持分步建模。") @Valid MainSource mainSource,
        @Schema(description = "补充同一对象属性的来源，最多 20 个。") @Size(max = 20) List<@Valid SupplementSource> supplements,
        @Schema(description = "对象内部属性展示分组，最多 30 个。") @Size(max = 30) List<@Valid PropertyGroup> groups,
        @Schema(description = "从已配置来源映射出的业务属性，最多 500 个。") @Size(max = 500) List<@Valid Property> properties,
        @Schema(description = "由当前对象类型维护的业务关系，最多 100 个。") @Size(max = 100) List<@Valid Relation> relations,
        @Schema(description = "查询、计算或 Action 的业务契约登记，最多 100 个；不会执行。") @Size(max = 100) List<@Valid Capability> capabilities
) {

    public BusinessObjectTypeDefinition {
        supplements = supplements == null ? List.of() : List.copyOf(supplements);
        groups = groups == null ? List.of() : List.copyOf(groups);
        properties = properties == null ? List.of() : List.copyOf(properties);
        relations = relations == null ? List.of() : List.copyOf(relations);
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
    }

    public static BusinessObjectTypeDefinition empty() {
        return new BusinessObjectTypeDefinition(null, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    @Schema(description = "主来源配置。一个对象类型只能有一个主来源。")
    public record MainSource(
            @Schema(description = "已发布数据模型 UUID。") UUID modelId,
            @Schema(description = "主来源中唯一标识字段 UUID；仅支持 STRING、BYTE、SHORT、INTEGER 或 LONG。") UUID identityFieldId,
            @Schema(description = "主来源中作为对象显示名称的字段 UUID。") UUID titleFieldId,
            @Schema(description = "主来源固定 AND 筛选条件。") @Size(max = 8) List<@Valid Filter> fixedFilters
    ) {
        public MainSource {
            fixedFilters = fixedFilters == null ? List.of() : List.copyOf(fixedFilters);
        }
    }

    @Schema(description = "一个补充来源。每个对象最多只能匹配一条记录。")
    public record SupplementSource(
            @Schema(description = "补充来源稳定 UUID，由客户端创建后保持不变。") UUID id,
            @Schema(description = "补充来源显示名称。") @Size(max = 100) String name,
            @Schema(description = "已发布数据模型 UUID。") UUID modelId,
            @Schema(description = "主来源和补充来源之间的等值匹配字段，至少一个时才会查询补充来源。") @Size(max = 8) List<@Valid FieldMapping> keyMappings,
            @Schema(description = "补充来源固定 AND 筛选条件。") @Size(max = 8) List<@Valid Filter> fixedFilters,
            @Schema(description = "可选的数据时间字段 UUID，用于展示本来源数据时间。") UUID dataTimeFieldId
    ) {
        public SupplementSource {
            keyMappings = keyMappings == null ? List.of() : List.copyOf(keyMappings);
            fixedFilters = fixedFilters == null ? List.of() : List.copyOf(fixedFilters);
        }
    }

    @Schema(description = "两个来源字段的等值匹配规则。")
    public record FieldMapping(
            @Schema(description = "主来源字段 UUID。") UUID mainFieldId,
            @Schema(description = "补充来源字段 UUID。") UUID supplementFieldId
    ) {
    }

    @Schema(description = "来源模型查询中使用的固定筛选条件。字段和值由来源字段类型解析，不接受 SQL 表达式。")
    public record Filter(
            @Schema(description = "来源模型字段 UUID。") UUID fieldId,
            @Schema(description = "过滤运算符，使用现有模型查询支持的 EQ、NE、GT、GE、LT、LE、IN、NOT_IN、BETWEEN、NOT_BETWEEN、LIKE、NOT_LIKE、IS_NULL、IS_NOT_NULL、IS_EMPTY 或 IS_NOT_EMPTY。") @Size(max = 32) String operator,
            @Schema(description = "单值或 BETWEEN 起始值。") Object value,
            @Schema(description = "BETWEEN 结束值。") Object secondValue,
            @Schema(description = "IN、NOT_IN 或 BETWEEN 使用的值集合。") @Size(max = 1000) List<Object> values
    ) {
        public Filter {
            values = values == null ? List.of() : List.copyOf(values);
        }
    }

    @Schema(description = "对象属性展示分组；只组织本对象属性，不产生对象或关系。")
    public record PropertyGroup(
            @Schema(description = "分组稳定 UUID。") UUID id,
            @Schema(description = "分组名称。") @Size(max = 100) String name,
            @Schema(description = "展示顺序，从 0 开始。") Integer sortOrder
    ) {
    }

    @Schema(description = "一个经过业务命名的对象属性。")
    public record Property(
            @Schema(description = "属性稳定 UUID。") UUID id,
            @Schema(description = "对象类型内唯一的技术编码。") @Size(max = 64) String code,
            @Schema(description = "业务显示名称。") @Size(max = 100) String name,
            @Schema(description = "业务说明。") @Size(max = 2000) String description,
            @Schema(description = "展示单位。") @Size(max = 32) String unit,
            @Schema(description = "所属分组 UUID；为空时显示在未分组。") UUID groupId,
            @Schema(description = "来源模型 UUID，必须为主来源或一个已配置补充来源。") UUID sourceModelId,
            @Schema(description = "来源模型字段 UUID。") UUID fieldId,
            @Schema(description = "展示顺序，从 0 开始。") Integer sortOrder
    ) {
    }

    @Schema(description = "数量约束是业务关系的辅助信息，不替代关系名称。")
    public enum RelationCardinality {
        ONE_TO_ONE,
        ONE_TO_MANY,
        MANY_TO_ONE
    }

    @Schema(description = "一个有业务含义的对象类型关系。具体关联在读取来源数据时按字段匹配解析。")
    public record Relation(
            @Schema(description = "关系稳定 UUID。") UUID id,
            @Schema(description = "当前对象类型内唯一的关系编码。") @Size(max = 64) String code,
            @Schema(description = "当前对象指向目标对象时的业务名称，例如所属水库。") @Size(max = 100) String forwardName,
            @Schema(description = "当前对象访问该关系时使用的稳定编码，在当前对象类型内唯一，例如 reservoir。") @Size(max = 64) String forwardAccessCode,
            @Schema(description = "从目标对象返回当前对象时的业务名称，例如拥有测站。") @Size(max = 100) String reverseName,
            @Schema(description = "目标对象反向访问该关系时使用的稳定编码，在目标对象类型内唯一，例如 stations。") @Size(max = 64) String reverseAccessCode,
            @Schema(description = "关系业务说明。") @Size(max = 2000) String description,
            @Schema(description = "目标对象类型 UUID。") UUID targetObjectTypeId,
            @Schema(description = "数量约束。ONE_TO_MANY 要求当前字段为当前对象唯一标识；MANY_TO_ONE 和 ONE_TO_ONE 要求目标字段为目标对象唯一标识。") RelationCardinality cardinality,
            @Schema(description = "当前对象主来源中用于匹配的字段 UUID。") UUID sourceFieldId,
            @Schema(description = "目标对象主来源中用于匹配的字段 UUID。") UUID targetFieldId
    ) {
    }

    @Schema(description = "能力类型，仅登记业务契约，不执行。")
    public enum CapabilityKind {
        QUERY,
        CALCULATION,
        ACTION
    }

    @Schema(description = "查询、计算或 Action 的业务契约。")
    public record Capability(
            @Schema(description = "能力稳定 UUID。") UUID id,
            @Schema(description = "对象类型内唯一的能力编码。") @Size(max = 64) String code,
            @Schema(description = "能力名称。") @Size(max = 100) String name,
            @Schema(description = "能力类型。") CapabilityKind kind,
            @Schema(description = "能力说明。") @Size(max = 5000) String description,
            @Schema(description = "输入参数。") @Size(max = 50) List<@Valid CapabilityField> inputs,
            @Schema(description = "输出字段。") @Size(max = 50) List<@Valid CapabilityField> outputs,
            @Schema(description = "业务前置条件说明。") @Size(max = 5000) String preconditions,
            @Schema(description = "Action 的预期业务效果；其他能力可为空。") @Size(max = 5000) String expectedEffect
    ) {
        public Capability {
            inputs = inputs == null ? List.of() : List.copyOf(inputs);
            outputs = outputs == null ? List.of() : List.copyOf(outputs);
        }
    }

    @Schema(description = "能力输入或输出字段。")
    public record CapabilityField(
            @Schema(description = "能力内唯一字段编码。") @Size(max = 64) String code,
            @Schema(description = "字段名称。") @Size(max = 100) String name,
            @Schema(description = "平台数据类型。") PlatformDataType fieldType,
            @Schema(description = "字段说明。") @Size(max = 1000) String description,
            @Schema(description = "输入字段是否必填；输出字段忽略。") Boolean required
    ) {
    }
}
