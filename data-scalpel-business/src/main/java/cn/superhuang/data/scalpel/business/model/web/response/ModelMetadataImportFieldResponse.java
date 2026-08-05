package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.business.standard.web.response.StandardDictionarySummaryResponse;

import java.util.List;

public record ModelMetadataImportFieldResponse(
        String key,
        int rowNumber,
        String code,
        String name,
        PlatformDataType fieldType,
        Integer length,
        Integer precision,
        Integer scale,
        GeometryTypeDefinition geometry,
        Boolean nullable,
        Boolean primaryKey,
        Integer sortOrder,
        String description,
        String standardDictionaryCode,
        StandardDictionarySummaryResponse standardDictionary,
        boolean importable,
        List<String> issues
) {

    public ModelMetadataImportFieldResponse {
        issues = List.copyOf(issues);
    }
}
