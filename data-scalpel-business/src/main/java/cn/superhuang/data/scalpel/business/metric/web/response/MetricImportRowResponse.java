package cn.superhuang.data.scalpel.business.metric.web.response;

import java.util.List;

public record MetricImportRowResponse(
        int rowNumber, String code, String name, String action,
        List<MetricImportChangeResponse> changes, List<MetricImportIssueResponse> issues
) {}
