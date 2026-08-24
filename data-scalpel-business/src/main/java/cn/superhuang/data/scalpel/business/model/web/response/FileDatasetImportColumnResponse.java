package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import cn.superhuang.data.scalpel.dialect.model.TypeMappingQuality;

import java.util.List;

public record FileDatasetImportColumnResponse(
        String sourceName,
        PlatformTypeDefinition sourceType,
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
        TypeMappingQuality mappingQuality,
        String mappingMessage,
        boolean importable,
        List<String> issues
) {

    public FileDatasetImportColumnResponse {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
