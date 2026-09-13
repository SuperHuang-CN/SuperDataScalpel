package cn.superhuang.data.scalpel.business.filedataset.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "指定文件数据集表当前解析得到的完整字段 Schema。")

public record FileDatasetSchemaResponse(
        @Schema(description = "该 Schema 所属文件数据集表 UUID。")
        UUID tableId,
        @Schema(description = "按文件或逻辑表原始顺序排列的字段定义。")
        List<FileDatasetFieldResponse> fields
) {
    public FileDatasetSchemaResponse {
        fields = List.copyOf(fields);
    }
}
