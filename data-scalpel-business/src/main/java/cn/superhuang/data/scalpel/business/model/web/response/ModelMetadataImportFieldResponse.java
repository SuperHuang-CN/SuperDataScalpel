package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;

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
        boolean importable,
        List<String> issues
) {

    public ModelMetadataImportFieldResponse {
        issues = List.copyOf(issues);
    }
}
