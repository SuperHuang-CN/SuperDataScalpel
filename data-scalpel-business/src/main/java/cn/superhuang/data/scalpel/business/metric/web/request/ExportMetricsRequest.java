package cn.superhuang.data.scalpel.business.metric.web.request;

import cn.superhuang.data.scalpel.business.metric.domain.MetricExportMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Read-only selection, intersected with the applied Search DSL when both are provided. */
public record ExportMetricsRequest(
        @NotNull MetricExportMode mode,
        @Size(max = 1000) List<@NotNull UUID> ids,
        @Size(max = 10000) String search
) {}
