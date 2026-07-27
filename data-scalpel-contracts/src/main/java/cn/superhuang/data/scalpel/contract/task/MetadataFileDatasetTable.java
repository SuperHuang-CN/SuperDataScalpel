package cn.superhuang.data.scalpel.contract.task;

import java.util.List;
import java.util.UUID;

public record MetadataFileDatasetTable(
        UUID id,
        String code,
        String name,
        FileDatasetType datasetType,
        FileDatasetParseStatus parseStatus,
        FileDatasetFileStatus fileStatus,
        List<CanvasColumnSchema> columns
) {
    public MetadataFileDatasetTable {
        columns = columns == null ? List.of() : List.copyOf(columns);
    }
}
