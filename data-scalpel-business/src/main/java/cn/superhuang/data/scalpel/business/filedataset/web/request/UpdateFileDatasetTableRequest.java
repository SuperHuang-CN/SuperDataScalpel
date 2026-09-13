package cn.superhuang.data.scalpel.business.filedataset.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "修改文件数据集逻辑表的显示名称，不改变来源、编码或字段 Schema。")

public record UpdateFileDatasetTableRequest(
        @Schema(description = "逻辑表显示名称。")
        @NotBlank @Size(max = 255) String name
) {
}
