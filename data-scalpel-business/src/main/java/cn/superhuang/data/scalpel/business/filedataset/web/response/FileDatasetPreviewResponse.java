package cn.superhuang.data.scalpel.business.filedataset.web.response;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public record FileDatasetPreviewResponse(
        List<FileDatasetFieldResponse> fields,
        List<List<Object>> rows,
        int limit,
        boolean truncated
) {
    public FileDatasetPreviewResponse {
        fields = List.copyOf(fields);
        rows = rows.stream().map(row -> Collections.unmodifiableList(new ArrayList<>(row))).toList();
    }
}
