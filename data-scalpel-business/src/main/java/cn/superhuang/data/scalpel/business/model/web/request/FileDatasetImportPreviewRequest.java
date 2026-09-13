package cn.superhuang.data.scalpel.business.model.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "将已解析文件数据集逻辑表的已保存 Schema 映射为 MANAGED 模型字段候选；不重新读取文件内容、不保存模型、不建表。")
public record FileDatasetImportPreviewRequest(
        @Schema(description = "文件数据集 UUID；服务端用于限定逻辑表归属，不按数据集自身汇总状态判断是否可预览。") @NotNull UUID fileDatasetId,
        @Schema(description = "属于该文件数据集、解析状态为 READY 或 SCHEMA_READY 且至少有一个已保存字段的逻辑表 UUID。") @NotNull UUID fileDatasetTableId,
        @Schema(description = "已启用、具有 STORAGE 用途且支持受管建表的目标 JDBC 数据源 UUID，用于验证目标物理类型映射；TDengine 当前不支持。") @NotNull UUID targetStorageDataSourceId
) {
}
