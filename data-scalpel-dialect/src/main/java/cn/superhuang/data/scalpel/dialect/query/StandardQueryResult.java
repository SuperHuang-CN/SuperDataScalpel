package cn.superhuang.data.scalpel.dialect.query;

import java.util.List;
import java.util.Map;

/** Raw JDBC execution result, ready to be adapted to the public service response. */
public record StandardQueryResult(Long totalCount, List<Map<String, Object>> rows) {

    public StandardQueryResult {
        rows = List.copyOf(rows);
    }
}
