package cn.superhuang.datascalpel.taskengine.httpapi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record HttpApiPullResult(List<List<Object>> rows, int pages, long responseBytes) {
    public HttpApiPullResult {
        Objects.requireNonNull(rows, "rows");
        rows = rows.stream().map(HttpApiPullResult::copyRow).toList();
    }

    private static List<Object> copyRow(List<Object> row) {
        return Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(row, "row")));
    }
}
