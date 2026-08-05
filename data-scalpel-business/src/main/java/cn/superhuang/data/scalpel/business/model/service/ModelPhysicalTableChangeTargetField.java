package cn.superhuang.data.scalpel.business.model.service;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;

import java.util.UUID;

/** Immutable target-field snapshot persisted with a physical-table change plan. */
public record ModelPhysicalTableChangeTargetField(
        UUID id,
        String code,
        String name,
        PlatformDataType fieldType,
        Integer length,
        Integer precision,
        Integer scale,
        GeometryTypeDefinition geometry,
        boolean nullable,
        boolean primaryKey,
        int sortOrder,
        String description,
        UUID standardDictionaryId
) {
    static ModelPhysicalTableChangeTargetField from(DataModelService.NormalizedField field) {
        return new ModelPhysicalTableChangeTargetField(
                field.input().id(), field.code(), field.input().name(), field.input().fieldType(),
                field.length(), field.precision(), field.scale(), field.geometry(),
                field.input().nullable(), field.input().primaryKey(),
                field.input().sortOrder(), field.input().description(),
                field.input().standardDictionaryId()
        );
    }
}
