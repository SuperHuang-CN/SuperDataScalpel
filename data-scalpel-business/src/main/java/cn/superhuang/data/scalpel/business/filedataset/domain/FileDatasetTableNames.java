package cn.superhuang.data.scalpel.business.filedataset.domain;

import java.util.Locale;

/** Shared normalization and comparison rules for logical-table names. */
public final class FileDatasetTableNames {

    public static final int MAX_LENGTH = 255;

    private FileDatasetTableNames() {
    }

    public static String normalize(String value) {
        if (value == null) {
            throw new IllegalArgumentException("表名称不能为空");
        }
        String stripped = value.strip();
        StringBuilder normalized = new StringBuilder(stripped.length());
        boolean whitespace = false;
        for (int offset = 0; offset < stripped.length();) {
            int codePoint = stripped.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                whitespace = !normalized.isEmpty();
                continue;
            }
            if (whitespace) {
                normalized.append('_');
                whitespace = false;
            }
            normalized.appendCodePoint(codePoint);
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("表名称不能为空");
        }
        if (normalized.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("表名称不能超过 " + MAX_LENGTH + " 个字符");
        }
        return normalized.toString();
    }

    public static String uniquenessKey(String value) {
        return normalize(value).toLowerCase(Locale.ROOT);
    }
}
