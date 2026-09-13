package cn.superhuang.data.scalpel.business.metric.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.metric.domain.MetricExportMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Read-only selection, intersected with the applied Search DSL when both are provided. */
@Schema(description = "指标元数据导出范围；支持按指定指标或当前搜索条件导出。")
public record ExportMetricsRequest(
        @Schema(description = "导出口径来源：DRAFT 使用当前草稿，PUBLISHED 使用当前发布版本；从未发布的指标不能按 PUBLISHED 导出。")
        @NotNull MetricExportMode mode,
        @Schema(description = "要导出的业务指标 UUID 列表，最多 1000 项；与 search 同时提供时取交集。")
        @Size(max = 1000) List<@NotNull UUID> ids,
        @Schema(description = "DataScalpel Search DSL；按搜索结果导出时使用，与 ids 同时提供时取交集。")
        @Size(max = 10000) String search
) {}
