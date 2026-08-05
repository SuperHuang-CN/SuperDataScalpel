package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;

import java.util.List;

public record JdbcQueryInspectionResponse(
        String analyzedSqlSha256,
        List<CanvasColumnSchema> columns
) {
    public JdbcQueryInspectionResponse {
        columns = List.copyOf(columns);
    }
}
