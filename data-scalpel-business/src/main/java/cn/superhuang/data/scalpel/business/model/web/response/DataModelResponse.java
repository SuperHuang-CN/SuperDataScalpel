package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelStatus;
import cn.superhuang.data.scalpel.business.model.domain.PhysicalTableMode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DataModelResponse(
        UUID id,
        String code,
        String name,
        UUID directoryId,
        ModelWarehouseLayerSummaryResponse warehouseLayer,
        UUID storageDataSourceId,
        String storageDataSourceName,
        String catalogName,
        String schemaName,
        String physicalTableName,
        PhysicalTableMode physicalTableMode,
        List<String> clickHouseOrderByColumns,
        DataModelStatus status,
        int schemaVersion,
        DataModelPhysicalStatisticsResponse physicalStatistics,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
    public static DataModelResponse from(
            DataModel model,
            String storageDataSourceName,
            ModelWarehouseLayerSummaryResponse warehouseLayer,
            DataModelPhysicalStatisticsResponse physicalStatistics
    ) {
        return new DataModelResponse(
                model.getId(), model.getCode(), model.getName(), model.getDirectoryId(),
                warehouseLayer,
                model.getStorageDataSourceId(), storageDataSourceName, model.getCatalogName(), model.getSchemaName(),
                model.getPhysicalTableName(), model.getPhysicalTableMode(), model.getClickHouseOrderByColumns(), model.getStatus(), model.getSchemaVersion(), physicalStatistics, model.getDescription(),
                model.getCreatedAt(), model.getUpdatedAt()
        );
    }

    public static DataModelResponse from(
            DataModel model,
            String storageDataSourceName,
            ModelWarehouseLayerSummaryResponse warehouseLayer
    ) {
        return from(model, storageDataSourceName, warehouseLayer, null);
    }

    public static DataModelResponse from(DataModel model, String storageDataSourceName) {
        return from(model, storageDataSourceName, null, null);
    }
}
