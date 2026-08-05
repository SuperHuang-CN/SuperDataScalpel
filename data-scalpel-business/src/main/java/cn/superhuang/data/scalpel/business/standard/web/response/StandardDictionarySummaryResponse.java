package cn.superhuang.data.scalpel.business.standard.web.response;

import cn.superhuang.data.scalpel.business.standard.domain.StandardDictionary;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;

import java.util.UUID;

public record StandardDictionarySummaryResponse(
        UUID id,
        String code,
        String name,
        PlatformDataType valueType,
        boolean enabled,
        int version
) {
    public static StandardDictionarySummaryResponse from(StandardDictionary dictionary) {
        if (dictionary == null) {
            return null;
        }
        return new StandardDictionarySummaryResponse(
                dictionary.getId(),
                dictionary.getCode(),
                dictionary.getName(),
                dictionary.getValueType(),
                dictionary.isEnabled(),
                dictionary.getVersion()
        );
    }
}
