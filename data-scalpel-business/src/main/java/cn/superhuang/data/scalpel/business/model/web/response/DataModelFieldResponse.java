package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.model.domain.DataModelFieldType;

import java.time.Instant;
import java.util.UUID;

public record DataModelFieldResponse(
        UUID id,
        UUID modelId,
        String code,
        String name,
        DataModelFieldType fieldType,
        Integer length,
        Integer precision,
        Integer scale,
        boolean nullable,
        boolean primaryKey,
        int sortOrder,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
    public static DataModelFieldResponse from(DataModelField field) {
        return new DataModelFieldResponse(
                field.getId(), field.getModelId(), field.getCode(), field.getName(), field.getFieldType(),
                field.getLength(), field.getPrecision(), field.getScale(), field.isNullable(), field.isPrimaryKey(),
                field.getSortOrder(), field.getDescription(), field.getCreatedAt(), field.getUpdatedAt()
        );
    }
}
