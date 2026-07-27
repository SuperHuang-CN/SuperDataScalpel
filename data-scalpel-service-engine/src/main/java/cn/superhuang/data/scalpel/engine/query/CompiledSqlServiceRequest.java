package cn.superhuang.data.scalpel.engine.query;

import cn.superhuang.data.scalpel.dialect.query.SqlQueryParameter;

import java.util.List;

record CompiledSqlServiceRequest(
        int pageNo,
        int pageSize,
        int offset,
        boolean returnCount,
        List<SqlQueryParameter> parameters
) {

    CompiledSqlServiceRequest {
        parameters = List.copyOf(parameters);
    }
}
