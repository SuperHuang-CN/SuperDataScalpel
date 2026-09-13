package cn.superhuang.data.scalpel.business.filedataset.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

@Schema(description = "文件数据集表的有序字段和受限样本行，不代表完整数据导出。")

public record FileDatasetPreviewResponse(
        @Schema(description = "预览行各位置对应的有序字段定义。")
        List<FileDatasetFieldResponse> fields,
        @Schema(description = "按 fields 顺序排列的预览行；每行元素位置与字段位置一一对应。")
        List<List<Object>> rows,
        @Schema(description = "本次预览允许返回的最大行数。")
        int limit,
        @Schema(description = "是否仍存在未返回的记录；true 表示 rows 只是样本，不能据此推断完整数据量。")
        boolean truncated
) {
    public FileDatasetPreviewResponse {
        fields = List.copyOf(fields);
        rows = rows.stream().map(row -> Collections.unmodifiableList(new ArrayList<>(row))).toList();
    }
}
