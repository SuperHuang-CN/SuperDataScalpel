package cn.superhuang.data.scalpel.business.filedataset.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetTableSource;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "文件数据集逻辑表中的一个已激活文件来源及兼容性指纹。")

public record FileDatasetTableSourceResponse(
        @Schema(description = "表来源记录 UUID。")
        UUID id,
        @Schema(description = "该来源当前归属的文件数据集表 UUID。")
        UUID tableId,
        @Schema(description = "提供该表来源数据的上传文件 UUID。")
        UUID sourceFileId,
        @Schema(description = "来源工作表、图层或对象条目的显示名称。")
        String sourceName,
        @Schema(description = "来源在文件内的稳定定位键，例如工作表名、图层名或对象条目路径。")
        String sourceKey,
        @Schema(description = "多个来源合并时的稳定顺序，数值越小越先参与合并。")
        int sourceOrder,
        @Schema(description = "该来源的 parser-reported rowCount；流式完整校验或带可靠行数元数据的格式通常精确，Excel、GDB 等有界抽样解析器可能只报告最多 1000 条样本数。")
        long rowCount,
        @Schema(description = "规范化字段 Schema 的内容指纹，用于校验追加或替换来源是否与当前表兼容。")
        String schemaFingerprint,
        @Schema(description = "该来源通过校验并原子进入逻辑表当前生效来源集合的时间，ISO-8601 UTC 时间戳，响应中的已激活来源始终有值。")
        Instant activatedAt,
        @Schema(description = "创建时间，ISO-8601 UTC 时间戳。")
        Instant createdAt,
        @Schema(description = "最后更新时间，ISO-8601 UTC 时间戳。")
        Instant updatedAt
) {
    public static FileDatasetTableSourceResponse from(FileDatasetTableSource source) {
        return new FileDatasetTableSourceResponse(
                source.getId(), source.getFileDatasetTableId(), source.getSourceFileId(),
                source.getSourceName(), source.getSourceKey(), source.getSourceOrder(),
                source.getRowCount(), source.getSchemaFingerprint(), source.getActivatedAt(),
                source.getCreatedAt(), source.getUpdatedAt()
        );
    }
}
