package cn.superhuang.data.scalpel.contract.service;

import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;

/** Public request body accepted by a published SQL query service. */
public record SqlServiceQueryRequest(
        Integer pageNo,
        Integer pageSize,
        Map<String, Object> arguments,
        Boolean returnCount
) {

    public SqlServiceQueryRequest {
        arguments = arguments == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
    }
}
