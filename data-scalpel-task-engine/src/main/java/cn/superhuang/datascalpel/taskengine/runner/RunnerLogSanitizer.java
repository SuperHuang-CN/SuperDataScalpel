package cn.superhuang.datascalpel.taskengine.runner;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

final class RunnerLogSanitizer {
    static final int MAX_STACK_BYTES = 64 * 1024;
    private static final String TRUNCATED = "\n...[STACK_TRUNCATED_AT_64_KIB]";
    private static final Pattern SENSITIVE_ASSIGNMENT = Pattern.compile(
            "(?i)(password|passwd|pwd|secret|token|credential|access[-_]?key|secret[-_]?key|signature|x-amz-signature)"
                    + "(\\s*[=:]\\s*)([\\\"']?)([^&\\s,;}\\\"']+)([\\\"']?)");
    private static final Pattern PRESIGNED_URL = Pattern.compile(
            "(?i)https?://\\S*(?:x-amz-|x-goog-|signature=|sig=|token=|access_key=)\\S*");
    private static final Pattern URL_USER_INFO = Pattern.compile("(?i)(https?://)[^/@\\s]+@");
    private static final Pattern S3_OBJECT_URI = Pattern.compile("(?i)s3a?://[^\\s,;)}\\]]+");
    private static final Pattern FILE_STORAGE_LOCATION = Pattern.compile(
            "(?i)(object[-_]?key|source[-_]?key|materialized[-_]?prefix|\\bkey)"
                    + "(\\s*[=:]\\s*)([\\\"']?)([^&\\s,;}\\\"']+)([\\\"']?)");
    private static final Pattern FILE_INPUT_TEMPORARY_PATH = Pattern.compile(
            "(?i)(?:[a-z]:)?[/\\\\][^\\s,;)}\\]]*datascalpel-file-input-[^\\s,;)}\\]]+");

    private RunnerLogSanitizer() {
    }

    static String sanitize(String value) {
        if (value == null) return null;
        String safe = PRESIGNED_URL.matcher(value).replaceAll("[redacted-presigned-url]");
        safe = SENSITIVE_ASSIGNMENT.matcher(safe).replaceAll("$1$2***");
        safe = S3_OBJECT_URI.matcher(safe).replaceAll("[redacted-object-uri]");
        safe = FILE_STORAGE_LOCATION.matcher(safe).replaceAll("$1$2***");
        safe = FILE_INPUT_TEMPORARY_PATH.matcher(safe).replaceAll("[redacted-file-input-temp-path]");
        return URL_USER_INFO.matcher(safe).replaceAll("$1***@");
    }

    static String stackTrace(Throwable throwable) {
        StringWriter buffer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(buffer));
        String safe = sanitize(buffer.toString());
        if (safe.getBytes(StandardCharsets.UTF_8).length <= MAX_STACK_BYTES) return safe;
        int limit = MAX_STACK_BYTES - TRUNCATED.getBytes(StandardCharsets.UTF_8).length;
        int end = 0;
        int bytes = 0;
        while (end < safe.length()) {
            int codePoint = safe.codePointAt(end);
            int length = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
            if (bytes + length > limit) break;
            bytes += length;
            end += Character.charCount(codePoint);
        }
        return safe.substring(0, end) + TRUNCATED;
    }

    static String safeMessage(String value, String fallback) {
        String safe = value == null || value.isBlank() ? fallback : sanitize(value.trim());
        return safe.substring(0, Math.min(1000, safe.length()));
    }
}
