package cn.superhuang.data.scalpel.business.dataentry.service;

import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAccessor;

/** Stable equality representation used only by data-entry business-key and lookup validation. */
final class DataEntryValueCanonicalizer {

    private DataEntryValueCanonicalizer() {
    }

    static String canonical(Object value, DataModelField field) {
        if (value == null) return "null";
        if (field.getFieldType() == PlatformDataType.TIMESTAMP) {
            Instant instant = instant(value);
            if (instant != null) return instant.toString();
        }
        if (value instanceof BigDecimal decimal) return decimal.stripTrailingZeros().toPlainString();
        if (value instanceof TemporalAccessor temporal) return temporal.toString();
        return String.valueOf(value);
    }

    private static Instant instant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime dateTime) return dateTime.toInstant();
        if (value instanceof ZonedDateTime dateTime) return dateTime.toInstant();
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        return null;
    }
}
