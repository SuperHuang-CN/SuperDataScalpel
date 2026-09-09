package cn.superhuang.data.scalpel.business.metric.web.response;

import java.util.List;

public record MetricImportPreviewResponse(
        String fingerprint, int totalRows, int createCount, int updateCount, int unchangedCount,
        int errorCount, int warningCount, boolean canImport, List<MetricImportRowResponse> rows
) {}
