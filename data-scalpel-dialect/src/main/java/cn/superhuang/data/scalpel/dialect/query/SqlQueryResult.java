package cn.superhuang.data.scalpel.dialect.query;

import java.util.List;
import java.util.Map;

/** Rows, optional total and actual result metadata from a SQL service query. */
public record SqlQueryResult(Long totalCount, List<Map<String, Object>> rows, QueryInspection inspection) {

    public SqlQueryResult {
        rows = List.copyOf(rows);
        if (inspection == null) {
            throw new IllegalArgumentException("SQL query inspection is required");
        }
    }
}
