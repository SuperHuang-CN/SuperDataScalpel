package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelStatus;
import cn.superhuang.data.scalpel.business.model.domain.PhysicalTableMode;

import java.time.Instant;
import java.util.UUID;

public record DataSourceRelatedModelResponse(
        UUID modelId,
        String modelCode,
        String modelName,
        DataModelStatus status,
        PhysicalTableMode physicalTableMode,
        String catalogName,
        String schemaName,
        String physicalTableName,
        int schemaVersion,
        Instant updatedAt
) {
    public static DataSourceRelatedModelResponse from(DataModel model) {
        return new DataSourceRelatedModelResponse(
                model.getId(), model.getCode(), model.getName(), model.getStatus(), model.getPhysicalTableMode(),
                model.getCatalogName(), model.getSchemaName(), model.getPhysicalTableName(), model.getSchemaVersion(),
                model.getUpdatedAt()
        );
    }
}
