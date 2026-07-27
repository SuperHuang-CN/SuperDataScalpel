package cn.superhuang.data.scalpel.contract.type;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * Database-neutral types used by model, task and service contracts.
 *
 * <p>The names intentionally follow Spark SQL scalar type semantics without depending on Spark runtime classes.</p>
 */
public enum PlatformDataType {

    BOOLEAN,
    BYTE,
    SHORT,
    INTEGER,
    LONG,
    FLOAT,
    DOUBLE,
    DECIMAL,
    STRING,
    BINARY,
    DATE,
    TIMESTAMP,
    TIMESTAMP_NTZ,
    GEOMETRY;

    @JsonCreator
    public static PlatformDataType fromJson(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "TEXT" -> STRING;
            case "DATETIME" -> TIMESTAMP_NTZ;
            default -> valueOf(value.trim().toUpperCase(Locale.ROOT));
        };
    }

    @JsonValue
    public String jsonValue() {
        return name();
    }
}
