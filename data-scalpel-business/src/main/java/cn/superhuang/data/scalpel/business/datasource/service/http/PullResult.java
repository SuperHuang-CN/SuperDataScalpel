package cn.superhuang.data.scalpel.business.datasource.service.http;

import java.util.List;

public record PullResult(
        List<String> columns,
        List<List<Object>> rows,
        int pageCount,
        long responseBytes,
        int lastHttpStatus,
        String contentType
) {
    public PullResult {
        columns = List.copyOf(columns);
        rows = rows.stream().map(List::copyOf).toList();
    }
}
