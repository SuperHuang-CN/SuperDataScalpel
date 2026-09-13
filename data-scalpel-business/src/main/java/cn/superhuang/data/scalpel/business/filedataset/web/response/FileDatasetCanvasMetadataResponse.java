package cn.superhuang.data.scalpel.business.filedataset.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "可供任务 Canvas 选择的文件数据集表及字段元数据。")

public record FileDatasetCanvasMetadataResponse(
        @Schema(description = "当前用户可在任务 Canvas 中选择的文件数据集表；只返回安全的字段投影，不包含存储地址、对象元数据或凭据。")
        List<FileDatasetCanvasTableMetadataResponse> tables
) {
    public FileDatasetCanvasMetadataResponse {
        tables = tables == null ? List.of() : List.copyOf(tables);
    }
}
