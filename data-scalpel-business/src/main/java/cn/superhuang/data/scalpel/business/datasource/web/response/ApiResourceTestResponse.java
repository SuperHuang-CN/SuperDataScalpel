package cn.superhuang.data.scalpel.business.datasource.web.response;

import java.util.List;

public record ApiResourceTestResponse(
        boolean success,
        String code,
        String message,
        long elapsedMs,
        Integer httpStatus,
        String contentType,
        int recordCount,
        List<String> columns,
        List<List<Object>> rows,
        ApiTestDiagnosticResponse diagnostic
) {
    public ApiResourceTestResponse {
        columns = columns == null ? List.of() : List.copyOf(columns);
        rows = rows == null ? List.of() : rows.stream().map(List::copyOf).toList();
    }
}
