package cn.superhuang.datascalpel.taskengine.httpapi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** One bounded group of converted HTTP API rows. */
public record HttpApiPullBatch(int batchNumber, int pageNumber, List<List<Object>> rows) {
    public HttpApiPullBatch {
        if (batchNumber < 1) throw new IllegalArgumentException("batchNumber must be positive");
        if (pageNumber < 0) throw new IllegalArgumentException("pageNumber must not be negative");
        Objects.requireNonNull(rows, "rows");
        if (rows.isEmpty()) throw new IllegalArgumentException("rows must not be empty");
        rows = rows.stream()
                .map(HttpApiPullBatch::copyRow)
                .toList();
    }

    private static List<Object> copyRow(List<Object> row) {
        return Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(row, "row")));
    }
}
