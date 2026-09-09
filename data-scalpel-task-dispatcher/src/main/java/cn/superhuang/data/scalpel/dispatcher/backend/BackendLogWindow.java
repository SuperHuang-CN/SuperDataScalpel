package cn.superhuang.data.scalpel.dispatcher.backend;

import java.nio.charset.StandardCharsets;

/** Keeps a UTF-8-safe tail window without retaining an ever-growing live log in memory. */
public final class BackendLogWindow {
    private BackendLogWindow() {
    }

    public static BackendLog recent(BackendLog source, int maximumLines, int maximumBytes) {
        byte[] original = source == null ? new byte[0] : source.content();
        String text = new String(original, StandardCharsets.UTF_8);
        boolean truncated = source != null && source.truncated();

        int start = lineStart(text, maximumLines);
        if (start > 0) {
            text = text.substring(start);
            truncated = true;
        }

        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maximumBytes) {
            int byteStart = bytes.length - maximumBytes;
            while (byteStart < bytes.length && (bytes[byteStart] & 0xC0) == 0x80) byteStart++;
            text = new String(bytes, byteStart, bytes.length - byteStart, StandardCharsets.UTF_8);
            bytes = text.getBytes(StandardCharsets.UTF_8);
            truncated = true;
        }
        return new BackendLog(bytes, truncated);
    }

    private static int lineStart(String text, int maximumLines) {
        if (maximumLines < 1 || text.isEmpty()) return text.length();
        int lineBreaks = 0;
        int index = text.length() - 1;
        if (index >= 0 && text.charAt(index) == '\n') index--;
        for (; index >= 0; index--) {
            if (text.charAt(index) == '\n' && ++lineBreaks == maximumLines) return index + 1;
        }
        return 0;
    }
}
