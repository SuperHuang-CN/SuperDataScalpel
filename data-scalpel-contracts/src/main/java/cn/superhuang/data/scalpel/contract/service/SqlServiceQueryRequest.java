package cn.superhuang.data.scalpel.contract.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;

/** Public request body accepted by a published SQL query service. */
public record SqlServiceQueryRequest(
        @JsonPropertyDescription("页码，从 1 开始。")
        Integer pageNo,
        @JsonPropertyDescription("每页最多返回的记录数。")
        Integer pageSize,
        @JsonPropertyDescription("按参数编码提供的试运行值。")
        Map<String, Object> arguments,
        @JsonPropertyDescription("是否额外计算并返回满足当前筛选条件的总记录数。")
        Boolean returnCount
) {

    public SqlServiceQueryRequest {
        arguments = arguments == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
    }
}
