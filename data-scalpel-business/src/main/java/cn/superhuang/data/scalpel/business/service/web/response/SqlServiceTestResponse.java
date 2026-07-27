package cn.superhuang.data.scalpel.business.service.web.response;

import cn.superhuang.data.scalpel.contract.service.ServiceQueryResponse;
import cn.superhuang.data.scalpel.contract.service.SqlServiceResultFieldDefinition;

import java.util.List;

public record SqlServiceTestResponse(
        boolean valid,
        List<SqlServiceTestProblem> problems,
        List<SqlServiceResultFieldDefinition> resultFields,
        ServiceQueryResponse preview,
        long elapsedMs
) {

    public SqlServiceTestResponse {
        problems = List.copyOf(problems);
        resultFields = List.copyOf(resultFields);
    }
}
