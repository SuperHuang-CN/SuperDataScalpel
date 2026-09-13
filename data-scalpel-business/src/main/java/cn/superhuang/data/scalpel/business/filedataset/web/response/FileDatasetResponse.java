package cn.superhuang.data.scalpel.business.filedataset.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDataset;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetType;

import java.time.Instant;
import java.util.UUID;

/** Dataset projection; physical object metadata is exposed by the nested files API. */
@Schema(description = "文件数据集基础资料、解析规则和文件及逻辑表数量摘要。")
public record FileDatasetResponse(
        @Schema(description = "文件数据集 UUID。")
        UUID id,
        @Schema(description = "所属目录 UUID；位于根目录时为空。")
        UUID directoryId,
        @Schema(description = "文件数据集显示名称。")
        String name,
        @Schema(description = "文件数据集的逻辑格式类型，决定允许上传的文件格式和 parsingOptions 结构。")
        FileDatasetType type,
        @Schema(description = "当前生效的格式专属解析选项；kind 与 type 对应。")
        FileDatasetParsingOptionsResponse parsingOptions,
        @Schema(description = "数据集中保留的上传文件记录数量。")
        long fileCount,
        @Schema(description = "数据集中已识别或创建的逻辑表总数。")
        long tableCount,
        @Schema(description = "解析状态为 READY 或 SCHEMA_READY、已形成权威 Schema 的逻辑表数量；其中 SCHEMA_READY 可能只支持任务读取 Schema，不能通过预览接口读取样本。")
        long readyTableCount,
        @Schema(description = "是否已有文件、逻辑表或 QUEUED/RUNNING 作业依赖当前解析选项；true 时仍可修改名称、目录和说明，但 parsingOptions 的规范化 JSON 不能改变。")
        boolean parsingOptionsLocked,
        @Schema(description = "用途说明；未填写时为空。")
        String description,
        @Schema(description = "创建时间，ISO-8601 UTC 时间戳。")
        Instant createdAt,
        @Schema(description = "最后更新时间，ISO-8601 UTC 时间戳。")
        Instant updatedAt
) {
    public static FileDatasetResponse from(
            FileDataset dataset,
            FileDatasetParsingOptionsResponse parsingOptions,
            long fileCount,
            long tableCount,
            long readyTableCount,
            boolean parsingOptionsLocked
    ) {
        return new FileDatasetResponse(
                dataset.getId(), dataset.getDirectoryId(), dataset.getName(), dataset.getType(), parsingOptions,
                fileCount, tableCount, readyTableCount, parsingOptionsLocked,
                dataset.getDescription(), dataset.getCreatedAt(), dataset.getUpdatedAt()
        );
    }
}
