package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import java.util.UUID;

@JsonClassDescription("供 Canvas 使用的文件数据集表元数据快照；包含文件准备与解析状态以及可读取的字段 Schema。")
public record MetadataFileDatasetTable(
        @JsonPropertyDescription("文件数据集表 UUID。")
        UUID id,
        @JsonPropertyDescription("文件数据集 UUID。")
        UUID fileDatasetId,
        @JsonPropertyDescription("文件数据集表的稳定技术编码。")
        String code,
        @JsonPropertyDescription("文件数据集表的显示名称。")
        String name,
        @JsonPropertyDescription("来源文件数据集类型，决定解析格式和读取能力。")
        FileDatasetType datasetType,
        @JsonPropertyDescription("表结构解析状态：QUEUED 等待、PARSING 解析中、SCHEMA_READY 已获得 Schema、READY 可供任务读取。")
        FileDatasetParseStatus parseStatus,
        @JsonPropertyDescription("底层文件准备状态：PREPARING 尚在准备，READY 已可读取。")
        FileDatasetFileStatus fileStatus,
        @JsonPropertyDescription("任务读取该表时可见的有序字段 Schema。")
        List<CanvasColumnSchema> columns
) {
    public MetadataFileDatasetTable {
        columns = columns == null ? List.of() : List.copyOf(columns);
    }

    public MetadataFileDatasetTable(
            UUID id,
            String code,
            String name,
            FileDatasetType datasetType,
            FileDatasetParseStatus parseStatus,
            FileDatasetFileStatus fileStatus,
            List<CanvasColumnSchema> columns
    ) {
        this(id, null, code, name, datasetType, parseStatus, fileStatus, columns);
    }
}
