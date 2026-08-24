package cn.superhuang.data.scalpel.contract.task;

import java.util.List;
import java.util.UUID;

public record MetadataFileDatasetTable(
        UUID id,
        UUID fileDatasetId,
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
