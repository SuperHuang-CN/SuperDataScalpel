package cn.superhuang.data.scalpel.contract.service;

import java.util.List;
import java.util.Map;

/** Common public page response used by standard and SQL query services. */
public record ServiceQueryResponse(
        int pageNo,
        int pageSize,
        Long totalCount,
        List<Map<String, Object>> resultList
) {

    public ServiceQueryResponse {
        resultList = List.copyOf(resultList);
    }
}
