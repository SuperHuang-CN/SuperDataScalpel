package cn.superhuang.data.scalpel.business.datasource.domain;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConnectionOptionsConverterTest {

    private final ConnectionOptionsConverter converter = new ConnectionOptionsConverter();

    @Test
    void roundTripsEncodedOptionKeysAndValues() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put("application.name", "数据 手术刀");
        options.put("ssl-mode", "require&verify=true");

        String encoded = converter.convertToDatabaseColumn(options);

        assertEquals(encoded.length(), ConnectionOptionsConverter.encodedLength(options));
        assertEquals(Map.copyOf(options), converter.convertToEntityAttribute(encoded));
    }

    @Test
    void rejectsValuesThatExceedThePersistenceColumnAfterEncoding() {
        Map<String, String> options = new LinkedHashMap<>();
        for (int index = 0; index < 8; index++) {
            options.put("custom" + index, "x".repeat(512));
        }

        assertThrows(IllegalArgumentException.class, () -> converter.convertToDatabaseColumn(options));
    }
}
