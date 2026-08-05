package cn.superhuang.data.scalpel.business.standard.web.response;

import cn.superhuang.data.scalpel.business.model.domain.DataModelStatus;
import cn.superhuang.data.scalpel.business.model.domain.PhysicalTableMode;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;

import java.util.UUID;

public record StandardDictionaryFieldReferenceResponse(
        UUID modelId,
        String modelCode,
        String modelName,
        DataModelStatus modelStatus,
        PhysicalTableMode physicalTableMode,
        int schemaVersion,
        UUID fieldId,
        String fieldCode,
        String fieldName,
        PlatformDataType fieldType
) {
}
