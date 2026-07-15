package cn.superhuang.data.scalpel.contract.service;

import java.util.List;
import java.util.Map;

/** Fixed public V1 response body for every standard table service. */
public record StandardServiceQueryResponse(
        int pageNo,
        int pageSize,
        Long totalCount,
        List<Map<String, Object>> resultList
) {

    public StandardServiceQueryResponse {
        resultList = List.copyOf(resultList);
    }
}
