package cn.superhuang.data.scalpel.business.metric.web.response;

public record MetricImportIssueResponse(int rowNumber, String column, String message, boolean blocking) {}
