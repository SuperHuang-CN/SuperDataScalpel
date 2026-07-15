package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.dialect.model.TypeMappingQuality;

public record ExternalTableImportColumnResponse(
        String name,
        String nativeType,
        PlatformDataType platformType,
        Integer length,
        Integer precision,
        Integer scale,
        boolean nullable,
        boolean primaryKey,
        TypeMappingQuality mappingQuality,
        String message,
        boolean importable,
        String comment
) {
}
