package cn.superhuang.data.scalpel.business.dataentry.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record DataEntryBusinessKeyMatch(Map<String, Object> key, long count) {

    public DataEntryBusinessKeyMatch {
        key = Collections.unmodifiableMap(new LinkedHashMap<>(key));
        if (count < 1) {
            throw new IllegalArgumentException("Business-key match count must be positive");
        }
    }
}
