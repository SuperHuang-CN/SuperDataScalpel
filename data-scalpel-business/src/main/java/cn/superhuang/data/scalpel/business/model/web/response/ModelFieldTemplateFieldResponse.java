package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.business.model.domain.ModelFieldTemplateItem;
import cn.superhuang.data.scalpel.business.standard.web.response.StandardDictionarySummaryResponse;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;

import java.util.UUID;

public record ModelFieldTemplateFieldResponse(
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
        StandardDictionarySummaryResponse standardDictionary
) {
    public static ModelFieldTemplateFieldResponse from(
            ModelFieldTemplateItem item,
            StandardDictionarySummaryResponse standardDictionary
    ) {
        return new ModelFieldTemplateFieldResponse(
                item.getId(), item.getCode(), item.getName(), item.getFieldType(),
                item.getLength(), item.getPrecision(), item.getScale(), item.getGeometry(),
                item.isNullable(), item.isPrimaryKey(), item.getSortOrder(), item.getDescription(),
                standardDictionary
        );
    }
}
