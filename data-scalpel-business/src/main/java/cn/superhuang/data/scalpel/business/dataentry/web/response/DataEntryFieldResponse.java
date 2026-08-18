package cn.superhuang.data.scalpel.business.dataentry.web.response;

import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.standard.web.response.StandardDictionarySummaryResponse;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;

import java.util.UUID;

public record DataEntryFieldResponse(
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
        String inputSource,
        StandardDictionarySummaryResponse standardDictionary,
        DataEntryLookupResponse lookup
) {
    public static DataEntryFieldResponse from(
            DataModelField field,
            StandardDictionarySummaryResponse dictionary,
            DataEntryLookupResponse lookup
    ) {
        return new DataEntryFieldResponse(
                field.getId(), field.getCode(), field.getName(), field.getFieldType(), field.getLength(),
                field.getPrecision(), field.getScale(), field.getGeometry(), field.isNullable(), field.isPrimaryKey(),
                field.getSortOrder(), field.getDescription(), dictionary != null ? "DICTIONARY" : lookup != null ? "MODEL_LOOKUP" : "DEFAULT",
                dictionary, lookup
        );
    }
}
