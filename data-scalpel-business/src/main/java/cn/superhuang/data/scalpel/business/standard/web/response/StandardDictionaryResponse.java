package cn.superhuang.data.scalpel.business.standard.web.response;

import cn.superhuang.data.scalpel.business.standard.domain.StandardDictionary;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;

import java.time.Instant;
import java.util.UUID;

public record StandardDictionaryResponse(
        UUID id,
        String code,
        String name,
        PlatformDataType valueType,
        boolean enabled,
        int version,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
    public static StandardDictionaryResponse from(StandardDictionary dictionary) {
        return new StandardDictionaryResponse(
                dictionary.getId(),
                dictionary.getCode(),
                dictionary.getName(),
                dictionary.getValueType(),
                dictionary.isEnabled(),
                dictionary.getVersion(),
                dictionary.getDescription(),
                dictionary.getCreatedAt(),
                dictionary.getUpdatedAt()
        );
    }
}
