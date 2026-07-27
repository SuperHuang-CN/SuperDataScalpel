package cn.superhuang.data.scalpel.business.datasource.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** Portable text storage for the small, driver-specific connection option map. */
@Converter
public class ConnectionOptionsConverter implements AttributeConverter<Map<String, String>, String> {

    public static final int MAX_DATABASE_COLUMN_LENGTH = 4000;

    @Override
    public String convertToDatabaseColumn(Map<String, String> attribute) {
        String encoded = encodeOptions(attribute);
        if (encoded != null && encoded.length() > MAX_DATABASE_COLUMN_LENGTH) {
            throw new IllegalArgumentException("JDBC 连接参数编码后不能超过 4000 个字符");
        }
        return encoded;
    }

    public static int encodedLength(Map<String, String> options) {
        String encoded = encodeOptions(options);
        return encoded == null ? 0 : encoded.length();
    }

    private static String encodeOptions(Map<String, String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        return attribute.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    @Override
    public Map<String, String> convertToEntityAttribute(String databaseData) {
        if (databaseData == null || databaseData.isBlank()) {
            return Map.of();
        }
        Map<String, String> options = new LinkedHashMap<>();
        for (String pair : databaseData.split("&")) {
            int separator = pair.indexOf('=');
            if (separator < 0) {
                throw new IllegalArgumentException("Invalid persisted connection option");
            }
            options.put(decode(pair.substring(0, separator)), decode(pair.substring(separator + 1)));
        }
        return Map.copyOf(options);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
