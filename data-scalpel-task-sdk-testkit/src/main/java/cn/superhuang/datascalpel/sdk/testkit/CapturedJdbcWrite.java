package cn.superhuang.datascalpel.sdk.testkit;

import cn.superhuang.datascalpel.sdk.JdbcTableIdentifier;
import cn.superhuang.datascalpel.sdk.JdbcWriteMode;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.types.StructType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CapturedJdbcWrite(
        String bindingName,
        JdbcTableIdentifier table,
        JdbcWriteMode mode,
        List<String> upsertKeyColumns,
        Map<String, String> columnMappings,
        StructType schema,
        List<Row> rows,
        long affectedRows
) {
    public CapturedJdbcWrite {
        upsertKeyColumns = List.copyOf(upsertKeyColumns);
        columnMappings = Collections.unmodifiableMap(new LinkedHashMap<>(columnMappings));
        rows = List.copyOf(rows);
    }
}
