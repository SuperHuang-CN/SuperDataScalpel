package cn.superhuang.datascalpel.sdk.testkit;

import cn.superhuang.datascalpel.sdk.ModelWriteMode;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.types.StructType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CapturedModelWrite(
        String bindingName,
        ModelWriteMode mode,
        Map<String, String> columnMappings,
        StructType schema,
        List<Row> rows,
        long affectedRows
) {
    public CapturedModelWrite {
        columnMappings = Collections.unmodifiableMap(new LinkedHashMap<>(columnMappings));
        rows = List.copyOf(rows);
    }
}
