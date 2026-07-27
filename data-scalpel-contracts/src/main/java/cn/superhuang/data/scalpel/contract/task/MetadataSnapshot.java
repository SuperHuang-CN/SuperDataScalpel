package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record MetadataSnapshot(
        List<MetadataDataSource> dataSources,
        List<MetadataModel> models,
        List<MetadataFileDatasetTable> fileDatasetTables
) {
    public MetadataSnapshot {
        dataSources = dataSources == null ? List.of() : List.copyOf(dataSources);
        models = models == null ? List.of() : List.copyOf(models);
        fileDatasetTables = fileDatasetTables == null ? List.of() : List.copyOf(fileDatasetTables);
    }

    public MetadataSnapshot(List<MetadataDataSource> dataSources, List<MetadataModel> models) {
        this(dataSources, models, List.of());
    }
}
