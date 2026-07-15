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
        UUID storageDataSourceId,
        String storageDataSourceName,
        String catalogName,
        String schemaName,
        String physicalTableName,
        PhysicalTableMode physicalTableMode,
        List<String> clickHouseOrderByColumns,
        DataModelStatus status,
        int schemaVersion,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
    public static DataModelResponse from(DataModel model, String storageDataSourceName) {
        return new DataModelResponse(
                model.getId(), model.getCode(), model.getName(), model.getDirectoryId(),
                model.getStorageDataSourceId(), storageDataSourceName, model.getCatalogName(), model.getSchemaName(),
                model.getPhysicalTableName(), model.getPhysicalTableMode(), model.getClickHouseOrderByColumns(), model.getStatus(), model.getSchemaVersion(), model.getDescription(),
                model.getCreatedAt(), model.getUpdatedAt()
        );
    }
}
