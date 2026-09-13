package cn.superhuang.data.scalpel.business.quality.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunTriggerType;
import cn.superhuang.data.scalpel.business.task.web.response.TaskRunExecutionErrorResponse;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityRuleSeverity;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityRuleType;
import cn.superhuang.data.scalpel.contract.quality.QualityConclusion;
import cn.superhuang.data.scalpel.contract.quality.ViolationMetric;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "一个模型最近的质检运行状态及最近一次有效质量结果。运行汇总来自管理库，规则明细按需读取同一次运行不超过 5 MiB 的不可变 result.json；只接受当前支持的 v4/v5 结果并校验运行身份、尝试次数和汇总一致性。")
public record ModelQualityOverviewResponse(
        @Schema(description = "模型 UUID。")
        UUID modelId,
        @Schema(description = "按 queuedAt 选择的该模型最近一次质检运行；从未创建过相关运行时为空。它可能仍在运行或以技术错误结束。")
        RecentRun latestRun,
        @Schema(description = "按 endedAt 选择的最近一次 SUCCESS 且包含质量结论和规则快照的运行汇总；较新的运行未完成或技术失败时仍保留该结果，从未产生时为空。")
        EffectiveResult latestEffectiveResult,
        @Schema(description = "latestEffectiveResult 对应不可变 result.json 制品的读取状态：AVAILABLE 已读取并通过运行身份与汇总校验；UNAVAILABLE 存储未配置、不可用或制品缺失；INVALID 制品超限、损坏或内容不一致；NOT_AVAILABLE 尚无有效运行。不会因制品不可用而回退更早运行。")
        ResultDetailStatus resultDetailStatus,
        @Schema(description = "质量结果不可用或失效的原因；可用时为空。")
        String resultDetailMessage,
        @Schema(description = "与 latestEffectiveResult 同一次运行的已执行和跳过规则明细；仅 resultDetailStatus=AVAILABLE 时有内容，否则为空列表且汇总仍可用。已执行规则按严重程度和名称排列，跳过规则随后按创建顺序排列。")
        List<RuleResult> ruleResults
) {
    public ModelQualityOverviewResponse {
        ruleResults = ruleResults == null ? List.of() : List.copyOf(ruleResults);
    }

    @Schema(description = "规则明细状态：AVAILABLE 已读取并校验不超过 5 MiB 的 v4/v5 result.json；UNAVAILABLE 对象存储未配置、不可用或制品缺失；INVALID 制品超限、损坏、版本不支持或与运行身份/汇总不一致；NOT_AVAILABLE 尚无有效质检运行。")
    public enum ResultDetailStatus { AVAILABLE, UNAVAILABLE, INVALID, NOT_AVAILABLE }

    @Schema(description = "单条规则在该次运行中的结果：PASSED 指标未超过阈值；FAILED 指标超过阈值；SKIPPED 因停用、失效或依赖不可用而未执行。")
    public enum RuleState { PASSED, FAILED, SKIPPED }

    @Schema(description = "失败样本状态：NOT_FAILED 规则通过；NOT_APPLICABLE 为 ROW_COUNT/FRESHNESS 等非行级失败；DISABLED 未开启样本；AVAILABLE 已生成受控 Parquet 样本。")
    public enum SampleStatus { NOT_FAILED, NOT_APPLICABLE, DISABLED, AVAILABLE }

    @Schema(description = "规则指标结构：VIOLATION 为行级违规数和比例；ROW_COUNT 为实际与最小行数；FRESHNESS 为最大时间值与延迟。")
    public enum MetricKind { VIOLATION, ROW_COUNT, FRESHNESS }

    @Schema(description = "该模型最近一次质检 TaskRun 的状态摘要，用于显示排队、运行或技术失败；不等同于最近有效质量结论。")
    public record RecentRun(
            @Schema(description = "任务运行 UUID。")
            UUID runId,
            @Schema(description = "任务 UUID。")
            UUID taskId,
            @Schema(description = "发起质检的任务名称；任务已删除时固定返回“任务已删除”。")
            String taskName,
            @Schema(description = "运行触发来源，例如手动、定时计划或工作流。")
            TaskRunTriggerType triggerType,
            @Schema(description = "TaskRun 技术执行状态；SUCCESS 只表示质检执行完成，数据仍可能不符合规则。")
            TaskRunStatus status,
            @Schema(description = "运行进入 Admin 队列的时间，ISO-8601 UTC 时间戳。")
            Instant queuedAt,
            @Schema(description = "Dispatcher 确认实际开始执行的时间，ISO-8601 UTC 时间戳；仍在排队或未启动即结束时为空。")
            Instant startedAt,
            @Schema(description = "运行进入终态的时间，ISO-8601 UTC 时间戳；仍在排队、运行或等待停止时为空。")
            Instant endedAt,
            @Schema(description = "当前运行状态或安全错误摘要；没有补充信息时为空。")
            String message,
            @Schema(description = "结构化技术执行错误；运行没有技术错误时为空。规则未通过不产生该错误。")
            TaskRunExecutionErrorResponse executionError
    ) {
    }

    @Schema(description = "最近一次成功完成的质检运行汇总；质量结论 FAILED 表示数据违规，不表示 TaskRun 技术失败。")
    public record EffectiveResult(
            @Schema(description = "任务运行 UUID。")
            UUID runId,
            @Schema(description = "任务 UUID。")
            UUID taskId,
            @Schema(description = "产生该结果的任务名称；任务已删除时固定返回“任务已删除”。")
            String taskName,
            @Schema(description = "质量结论：PASSED 表示没有规则失败；FAILED 表示至少一条规则超过阈值。")
            QualityConclusion conclusion,
            @Schema(description = "本次规则快照总数，等于 passedRules、failedRules 和 skippedRules 之和。")
            long totalRules,
            @Schema(description = "通过的质量规则数量。")
            long passedRules,
            @Schema(description = "未通过的质量规则数量。")
            long failedRules,
            @Schema(description = "跳过的质量规则数量。")
            long skippedRules,
            @Schema(description = "目标模型在该次全量质检中读取的行数，也是行级违规比例的分母。")
            long checkedRows,
            @Schema(description = "Admin 固化模型、规则和依赖快照的时间；后续规则变更不会改写该历史结果。")
            Instant ruleSnapshotAt,
            @Schema(description = "该次运行进入 Admin 队列的时间。")
            Instant queuedAt,
            @Schema(description = "该次成功运行结束并形成质量结果的时间。")
            Instant endedAt
    ) {
    }

    @Schema(description = "最近有效运行中一条已执行或跳过规则的不可变快照结果。")
    public record RuleResult(
            @Schema(description = "运行时固化的质量规则 UUID；即使当前规则已删除仍保留。")
            UUID ruleId,
            @Schema(description = "运行时固化的质量规则名称。")
            String ruleName,
            @Schema(description = "运行时固化的规则类型。")
            ModelQualityRuleType ruleType,
            @Schema(description = "已执行规则的严重程度；SKIPPED 规则的结果制品不携带该值，因此为空。")
            ModelQualityRuleSeverity severity,
            @Schema(description = "该次不可变规则快照的判定结果：PASSED 已执行且指标未超过阈值；FAILED 已执行且指标超过阈值；SKIPPED 因规则停用、失效或运行依赖不可用而未执行。")
            RuleState state,
            @Schema(description = "该规则在 Runner 中计算的耗时，单位毫秒；跳过规则为空。")
            Long durationMs,
            @Schema(description = "已执行规则的强类型指标；跳过规则为空。")
            Metric metric,
            @Schema(description = "规则跳过的稳定原因码；未跳过时为空。")
            String skipCode,
            @Schema(description = "规则未执行的可读原因；未跳过时为空。")
            String skipReason,
            @Schema(description = "结果制品 v5 的失败样本描述；跳过规则或历史 v4 结果为空。")
            Sample sample
    ) {
    }

    @Schema(description = "按 kind 区分的规则指标；与当前 kind 无关的字段均为空。")
    public record Metric(
            @Schema(description = "指标类型，决定其余字段组合。")
            MetricKind kind,
            @Schema(description = "VIOLATION 指标的违规行数，范围 0 到 checkedRows；其他类型为空。")
            Long violationCount,
            @Schema(description = "VIOLATION 指标的违规百分比，等于 violationCount / checkedRows × 100，空表时为 0，范围 0 到 100。")
            BigDecimal violationPercent,
            @Schema(description = "VIOLATION 规则阈值单位：COUNT 按违规行数，PERCENT 按违规百分比；其他类型为空。")
            ViolationMetric toleranceMetric,
            @Schema(description = "VIOLATION 规则允许的最大违规数或百分比；实际值小于或等于该值时通过。")
            BigDecimal toleranceValue,
            @Schema(description = "ROW_COUNT 指标读取到的目标模型总行数；其他类型为空。")
            Long actualRows,
            @Schema(description = "行数质量规则要求的最小行数。")
            Long minimumRows,
            @Schema(description = "FRESHNESS 字段在全部非空值中的最大时间，ISO-8601 UTC 时间戳；字段没有非空值时为空并判定失败，其他指标类型为空。")
            Instant maximumValue,
            @Schema(description = "FRESHNESS 最大时间到 Runner 启动时间的延迟分钟，向上取整；未来或相同时间按 0，字段全空时为空。")
            Long actualDelayMinutes,
            @Schema(description = "FRESHNESS 规则允许的最大延迟分钟；实际延迟小于或等于该值时通过。")
            Long maximumDelayMinutes
    ) {
        public static Metric violation(
                long violationCount,
                BigDecimal violationPercent,
                ViolationMetric toleranceMetric,
                BigDecimal toleranceValue
        ) {
            return new Metric(MetricKind.VIOLATION, violationCount, violationPercent,
                    toleranceMetric, toleranceValue, null, null, null, null, null);
        }

        public static Metric rowCount(long actualRows, long minimumRows) {
            return new Metric(MetricKind.ROW_COUNT, null, null, null, null,
                    actualRows, minimumRows, null, null, null);
        }

        public static Metric freshness(Instant maximumValue, Long actualDelayMinutes, long maximumDelayMinutes) {
            return new Metric(MetricKind.FRESHNESS, null, null, null, null,
                    null, null, maximumValue, actualDelayMinutes, maximumDelayMinutes);
        }
    }

    @Schema(description = "规则失败样本的非敏感摘要；实际业务行保存在受保护的 Parquet 制品中，不由该接口返回。")
    public record Sample(
            @Schema(description = "样本生成状态；只有 AVAILABLE 时其余字段有值。")
            SampleStatus status,
            @Schema(description = "Parquet 中保存的失败样本行数，最多 1000；非 AVAILABLE 时为空。")
            Long sampledRows,
            @Schema(description = "该规则检查出的全部违规行数；非 AVAILABLE 时为空。")
            Long violationRows,
            @Schema(description = "失败样本是否因 1000 行上限而只包含部分违规行，即 sampledRows < violationRows；非 AVAILABLE 时为空。")
            Boolean truncated,
            @Schema(description = "样本是否包含模型全部主键字段，可用于定位原始行；模型无主键或主键含 Binary/Geometry 时为 false，非 AVAILABLE 时为空。")
            Boolean rowLocatable
    ) {
    }
}
