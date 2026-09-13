package cn.superhuang.data.scalpel.business.filedataset.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFileStatus;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseStatus;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetType;

import java.util.List;
import java.util.UUID;

/**
 * Safe Canvas projection of a file dataset table.
 *
 * <p>Storage locations, parsing options, object metadata and credentials are deliberately absent.</p>
 */
@Schema(description = "任务 Canvas 可安全引用的一张文件数据集表及其字段 Schema。")
public record FileDatasetCanvasTableMetadataResponse(
        @Schema(description = "Canvas 可绑定的文件数据集表 UUID。")
        UUID fileDatasetTableId,
        @Schema(description = "该表所属文件数据集的 UUID。")
        UUID fileDatasetId,
        @Schema(description = "所属文件数据集的显示名称。")
        String fileDatasetName,
        @Schema(description = "所属文件数据集的逻辑格式类型，例如 CSV、JSON、Parquet、Excel、GDB 或 SHP。")
        FileDatasetType datasetType,
        @Schema(description = "该表在所属文件数据集内的稳定技术编码。")
        String code,
        @Schema(description = "该表的显示名称。")
        String name,
        @Schema(description = "表的解析进度：QUEUED 等待解析，PARSING 正在解析，SCHEMA_READY 仅 Schema 可用，READY 同时支持数据预览。")
        FileDatasetParseStatus parseStatus,
        @Schema(description = "所有当前来源文件的汇总准备状态：仅当每个来源文件都存在且为 READY 时返回 READY，否则返回 PREPARING；不代表表解析已经完成。")
        FileDatasetFileStatus fileStatus,
        @Schema(description = "任务读取该表时可见的有序字段 Schema。")
        List<FileDatasetFieldResponse> fields
) {
    public FileDatasetCanvasTableMetadataResponse {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }
}
