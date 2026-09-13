package cn.superhuang.data.scalpel.business.filedataset.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Read-only batch lookup used by the Canvas designer. */
@Schema(description = "批量查询文件数据集表的 Canvas 输入元数据。")
public record QueryFileDatasetCanvasMetadataRequest(
        @Schema(description = "要查询的文件数据集表 UUID 列表，最多 200 项；null 会被忽略，重复 UUID 按首次出现位置去重。不存在或内部来源关系不完整的表不会出现在响应中。")
        @NotEmpty
        @Size(max = 200)
        List<UUID> fileDatasetTableIds
) {
}
