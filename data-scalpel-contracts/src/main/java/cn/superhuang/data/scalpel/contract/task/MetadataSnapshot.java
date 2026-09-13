package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("任务编译时固定的数据源、模型和文件表安全元数据集合；用于解析资源绑定，不会在编译中重新读取业务库。")
public record MetadataSnapshot(
        @JsonPropertyDescription("任务运行需要的数据源安全快照。")
        List<MetadataDataSource> dataSources,
        @JsonPropertyDescription("任务编译和运行允许引用的模型、字段及物理表安全快照。")
        List<MetadataModel> models,
        @JsonPropertyDescription("任务运行需要的文件数据集表安全快照。")
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
