package cn.superhuang.data.scalpel.business.service;

import cn.superhuang.data.scalpel.business.service.web.response.SqlServiceTestProblem;
import cn.superhuang.data.scalpel.contract.service.SqlServiceResultFieldDefinition;

import java.util.List;

record SqlServiceInspection(
        String jdbcSql,
        List<String> bindingOrder,
        List<SqlServiceResultFieldDefinition> resultFields,
        List<SqlServiceTestProblem> problems
) {

    SqlServiceInspection {
        bindingOrder = List.copyOf(bindingOrder);
        resultFields = List.copyOf(resultFields);
        problems = List.copyOf(problems);
    }

    boolean valid() {
        return problems.isEmpty();
    }
}
