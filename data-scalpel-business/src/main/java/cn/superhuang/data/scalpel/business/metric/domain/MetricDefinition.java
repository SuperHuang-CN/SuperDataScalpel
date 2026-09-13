package cn.superhuang.data.scalpel.business.metric.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

/** Business documentation and materialized-result metadata; never an executable expression. */
@Schema(description = "指标的业务口径、统计规则和结果位置说明；这是治理文档，不是可执行表达式。")
public record MetricDefinition(
        @Schema(description = "指标表达的业务含义；发布前必填，最长 10000 字符。") @Size(max=10000) String businessMeaning,
        @Schema(description = "计算口径的文字说明；发布前必填，最长 20000 字符，不会作为 SQL 或表达式执行。") @Size(max=20000) String calculation,
        @Schema(description = "统计对象、纳入与排除范围；发布前必填，最长 10000 字符。") @Size(max=10000) String statisticalScope,
        @Schema(description = "时间字段、自然期或业务时间的解释；有统计周期时发布前必填。") @Size(max=10000) String timeDescription,
        @Schema(description = "来源数据的原始粒度说明。") @Size(max=2000) String sourceGrain,
        @Schema(description = "指标结果每一行所代表的业务粒度；发布前必填，最长 2000 字符。") @Size(max=2000) String grainDescription,
        @Schema(description = "指标值单位，例如元、户、平方公里；发布前必填，最长 32 字符。") @Size(max=32) String unit,
        @Schema(description = "统计周期：NONE 无固定周期，或 DAY、WEEK、MONTH、QUARTER、YEAR；省略时为 NONE。") Period statisticalPeriod,
        @Schema(description = "字符串周期字段的 DateTimeFormatter 格式，例如 yyyy-MM；绑定 STRING 类型 periodFieldId 时发布前必填且必须是合法格式，其他情况可为空。") @Size(max=100) String periodFormat,
        @Schema(description = "来源为空、缺失或分母为零时的处理口径；发布前必填，最长 10000 字符。") @Size(max=10000) String nullHandling,
        @Schema(description = "跨维度或跨周期汇总时采用的业务规则；发布前必填，最长 10000 字符。") @Size(max=10000) String aggregationDescription,
        @Schema(description = "指标数据的更新频率、延迟或重算方式说明。") @Size(max=2000) String updateDescription,
        @Schema(description = "展示建议的小数位数，范围 0 到 10；省略时为 2。") @Min(0) @Max(10) Integer decimalPlaces,
        @Schema(description = "值展示方式：NUMBER 普通数值，RATIO 0 到 1 的比值，PERCENT_VALUE 已乘 100 的百分数值；省略时为 NUMBER。") ValueFormat valueFormat,
        @Schema(description = "已物化指标结果所在的模型与字段；可为空，未绑定结果位置不会阻止发布，此时健康状态为 UNBOUND。") @Valid Binding binding,
        @Schema(description = "口径引用的模型、字段、指标或数据服务，最多 50 项；仅用于治理说明和导航。") @Size(max=50) List<@NotNull @Valid Reference> references
) {
    public MetricDefinition {
        statisticalPeriod = statisticalPeriod == null ? Period.NONE : statisticalPeriod;
        valueFormat = valueFormat == null ? ValueFormat.NUMBER : valueFormat;
        decimalPlaces = decimalPlaces == null ? 2 : decimalPlaces;
        references = references == null ? List.of() : List.copyOf(references);
    }
    public static MetricDefinition empty() {
        return new MetricDefinition(null,null,null,null,null,null,null,Period.NONE,null,null,null,null,2,ValueFormat.NUMBER,null,List.of());
    }
    public enum Period { NONE, DAY, WEEK, MONTH, QUARTER, YEAR }
    public enum ValueFormat { NUMBER, RATIO, PERCENT_VALUE }
    public enum Operator { EQ, NE, GT, GE, LT, LE, IS_NULL, IS_NOT_NULL }
    public enum ResourceKind { MODEL, MODEL_FIELD, METRIC, DATA_SERVICE }
    @Schema(description = "指标结果在一个已发布模型中的字段绑定。")
    public record Binding(
            @Schema(description = "承载指标结果的已发布模型 UUID；只要提供 binding，发布时必填。当前实现按模型状态校验，不额外限制 MANAGED 或 EXTERNAL 物理模式。") UUID modelId,
            @Schema(description = "模型中保存指标数值的数值类型字段 UUID；只要提供 binding，发布时必填。") UUID valueFieldId,
            @Schema(description = "模型中保存统计周期的 STRING、DATE、TIMESTAMP 或 TIMESTAMP_NTZ 字段 UUID；statisticalPeriod 不是 NONE 时发布前必填。") UUID periodFieldId,
            @Schema(description = "结果维度字段及业务名称，最多 20 项。") @Size(max=20) List<@NotNull @Valid Dimension> dimensions,
            @Schema(description = "解释结果所需但不属于值、周期或维度的辅助字段 UUID，最多 4 项。") @Size(max=4) List<@NotNull UUID> supportingFieldIds,
            @Schema(description = "识别同一物理结果表中当前指标记录的固定条件，最多 8 项。") @Size(max=8) List<@NotNull @Valid FixedFilter> fixedFilters) {
        public Binding {
            dimensions = dimensions == null ? List.of() : List.copyOf(dimensions);
            supportingFieldIds = supportingFieldIds == null ? List.of() : List.copyOf(supportingFieldIds);
            fixedFilters = fixedFilters == null ? List.of() : List.copyOf(fixedFilters);
        }
    }
    @Schema(description = "指标结果中的一个分析维度。")
    public record Dimension(
            @Schema(description = "维度稳定键，必须匹配 [a-z][a-z0-9_]{0,63} 且在本指标内唯一。") @Size(max=64) String key,
            @Schema(description = "维度业务名称；发布时不能为空。") @Size(max=100) String name,
            @Schema(description = "维度值含义或分类规则。") @Size(max=1000) String description,
            @Schema(description = "结果模型中的维度字段 UUID；发布时必填，同一字段不能被多个维度重复使用，BINARY 和 GEOMETRY 字段不能作为维度。") UUID fieldId) {}
    @Schema(description = "指标结果记录必须满足的固定字段条件。")
    public record FixedFilter(
            @Schema(description = "结果模型中的条件字段 UUID。") UUID fieldId,
            @Schema(description = "比较操作；发布时必填。IS_NULL 和 IS_NOT_NULL 检查空值，其余操作按字段类型比较；BOOLEAN 只支持 EQ 和 NE。") Operator operator,
            @Schema(description = "按字段平台类型解析的比较值；IS_NULL 和 IS_NOT_NULL 时必须为空或空字符串，其他操作时必填。DATE、TIMESTAMP、TIMESTAMP_NTZ 分别使用 ISO LocalDate、OffsetDateTime、LocalDateTime 格式；BINARY 和 GEOMETRY 不支持固定条件。") @Size(max=2000) String value) {}
    @Schema(description = "指标口径引用的治理资源。")
    public record Reference(
            @Schema(description = "资源类型：MODEL、MODEL_FIELD、METRIC 或 DATA_SERVICE。") @NotNull ResourceKind resourceKind,
            @Schema(description = "被引用资源的 UUID。") @NotNull UUID resourceId,
            @Schema(description = "引用模型字段时所属模型 UUID；其他资源类型为空。") UUID parentModelId,
            @Schema(description = "引用特定指标发布版本时的版本号；引用当前资源时为空。") @Min(1) Integer targetVersion,
            @Schema(description = "该资源在口径中的角色，例如来源、分母或参考服务。") @Size(max=100) String role,
            @Schema(description = "引用原因和使用方式说明。") @Size(max=2000) String note) {}
}
