package cn.superhuang.data.scalpel.dialect.runtime;

import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.Base64;

final class JdbcValueNormalizer {

    private static final int MAX_TEXT_LENGTH = 2000;
    private static final int MAX_BINARY_LENGTH = 512;

    private JdbcValueNormalizer() {
    }

    static Object normalize(Object value) throws SQLException {
        if (value == null || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof byte[] bytes) {
            byte[] displayed = bytes.length > MAX_BINARY_LENGTH ? Arrays.copyOf(bytes, MAX_BINARY_LENGTH) : bytes;
            String encoded = Base64.getEncoder().encodeToString(displayed);
            return bytes.length > displayed.length ? encoded + "…" : encoded;
        }
        if (value instanceof Blob blob) {
            int length = (int) Math.min(blob.length(), MAX_BINARY_LENGTH);
            return normalize(blob.getBytes(1, length));
        }
        if (value instanceof Clob clob) {
            return truncate(clob.getSubString(1, (int) Math.min(clob.length(), MAX_TEXT_LENGTH + 1L)));
        }
        if (value instanceof SQLXML sqlxml) {
            return truncate(sqlxml.getString());
        }
        if (value instanceof Array array) {
            Object raw = array.getArray();
            return raw instanceof Object[] values
                    ? Arrays.stream(values).map(String::valueOf).toList()
                    : truncate(String.valueOf(raw));
        }
        if (value instanceof TemporalAccessor
                || value instanceof java.sql.Date
                || value instanceof java.sql.Time
                || value instanceof java.sql.Timestamp) {
            return value.toString();
        }
        return truncate(String.valueOf(value));
    }

    private static String truncate(String value) {
        return value.length() > MAX_TEXT_LENGTH ? value.substring(0, MAX_TEXT_LENGTH) + "…" : value;
    }
}
