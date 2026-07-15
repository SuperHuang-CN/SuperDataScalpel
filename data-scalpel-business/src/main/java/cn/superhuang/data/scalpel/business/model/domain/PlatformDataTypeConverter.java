package cn.superhuang.data.scalpel.business.model.domain;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Reads legacy TEXT/DATETIME values while all new writes use the canonical platform type names. */
@Converter
public class PlatformDataTypeConverter implements AttributeConverter<PlatformDataType, String> {

    @Override
    public String convertToDatabaseColumn(PlatformDataType attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public PlatformDataType convertToEntityAttribute(String value) {
        return PlatformDataType.fromJson(value);
    }
}
