package cn.superhuang.data.scalpel.business.filedataset.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetCompression;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFile;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFormat;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFileStatus;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetStorageKind;

import java.time.Instant;
import java.util.UUID;

/** API projection intentionally excludes the internal object key and storage ETag. */
@Schema(description = "文件数据集中的一个上传文件及其规范化准备状态；不暴露内部对象存储位置。")
public record FileDatasetFileResponse(
        @Schema(description = "上传文件记录 UUID。")
        UUID id,
        @Schema(description = "该物理文件所属文件数据集的 UUID。")
        UUID fileDatasetId,
        @Schema(description = "上传时的原始文件名。")
        String originalFileName,
        @Schema(description = "文件实际物理格式，例如 CSV、JSONL、PARQUET、GPKG、GDB 或 SHP；由上传类型、扩展名和内容签名共同确认。")
        FileDatasetFormat format,
        @Schema(description = "文件外层压缩格式；NONE 表示未压缩，GZIP 表示单文件 .gz，ZIP 表示 GDB 或 SHP 组件归档，不会为空。")
        FileDatasetCompression compression,
        @Schema(description = "HTTP 媒体类型。")
        String contentType,
        @Schema(description = "内容大小，单位字节。")
        long sizeBytes,
        @Schema(description = "文件准备状态：PREPARING 正在展开或整理，READY 已形成解析器可读取的规范存储。")
        FileDatasetFileStatus status,
        @Schema(description = "解析器看到的规范化存储形态：单对象、展开后的 GDB 目录或完整 Shapefile 组件集合。")
        FileDatasetStorageKind storageKind,
        @Schema(description = "压缩包展开或文件规范化后的总内容大小，单位字节；无需准备或尚未完成时为空。")
        Long materializedSizeBytes,
        @Schema(description = "归一化准备后产生的可解析条目数量，例如压缩包展开后的文件或图层数量；无需展开时为空。")
        Integer materializedEntryCount,
        @Schema(description = "文件处于 PREPARING 时负责展开或整理它的当前作业 UUID；准备结束后为空。")
        UUID currentPreparationJobId,
        @Schema(description = "创建时间，ISO-8601 UTC 时间戳。")
        Instant createdAt,
        @Schema(description = "最后更新时间，ISO-8601 UTC 时间戳。")
        Instant updatedAt
) {
    public static FileDatasetFileResponse from(FileDatasetFile file) {
        return new FileDatasetFileResponse(
                file.getId(), file.getFileDatasetId(), file.getOriginalFileName(), file.getFormat(), file.getCompression(),
                file.getContentType(), file.getSizeBytes(), file.getStatus(), file.getStorageKind(),
                file.getMaterializedSizeBytes(), file.getMaterializedEntryCount(), file.getCurrentPreparationJobId(),
                file.getCreatedAt(), file.getUpdatedAt()
        );
    }
}
