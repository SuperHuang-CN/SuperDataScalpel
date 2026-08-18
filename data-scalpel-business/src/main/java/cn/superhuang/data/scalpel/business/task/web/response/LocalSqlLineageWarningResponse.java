package cn.superhuang.data.scalpel.business.task.web.response;

/** Non-blocking loss of confidence reported by Local SQL lineage analysis. */
public record LocalSqlLineageWarningResponse(String code, String message, Integer outputOrdinal) {
}
