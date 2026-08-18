package cn.superhuang.data.scalpel.business.quality.web.response;

import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.standard.web.response.StandardDictionarySummaryResponse;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;

import java.util.UUID;

public record ModelQualityRuleFieldResponse(
        UUID id,
        String code,
        String name,
        PlatformDataType fieldType,
        StandardDictionarySummaryResponse standardDictionary
) {
    public static ModelQualityRuleFieldResponse from(
            DataModelField field,
            StandardDictionarySummaryResponse standardDictionary
    ) {
        return new ModelQualityRuleFieldResponse(
                field.getId(), field.getCode(), field.getName(), field.getFieldType(), standardDictionary
        );
    }
}
