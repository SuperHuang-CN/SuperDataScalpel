package cn.superhuang.datascalpel.taskengine.runner;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
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
    private static final Pattern FILE_OUTPUT_TEMPORARY_PATH = Pattern.compile(
            "(?i)(?:[a-z]:)?[/\\\\][^\\s,;)}\\]]*datascalpel-(?:shapefile|geojson)-[^\\s,;)}\\]]+");
    private static final Pattern SPARK_TEMPORARY_PATH = Pattern.compile(
            "(?i)(?:[a-z]:)?[/\\\\][^\\s,;)}\\]]*(?:spark|blockmgr)-[^\\s,;)}\\]]+");
    private static final Pattern THROWABLE_MESSAGE_LINE = Pattern.compile(
            "(?m)^(\\s*(?:(?:Caused by|Suppressed):\\s+)?"
                    + "[A-Za-z_$][A-Za-z0-9_.$]*(?:Exception|Error))(?::.*)?$");

    private RunnerLogSanitizer() {
    }

    static String sanitize(String value) {
        if (value == null) return null;
        String safe = PRESIGNED_URL.matcher(value).replaceAll("[redacted-presigned-url]");
        safe = SENSITIVE_ASSIGNMENT.matcher(safe).replaceAll("$1$2***");
        safe = S3_OBJECT_URI.matcher(safe).replaceAll("[redacted-object-uri]");
        safe = FILE_STORAGE_LOCATION.matcher(safe).replaceAll("$1$2***");
        safe = FILE_INPUT_TEMPORARY_PATH.matcher(safe).replaceAll("[redacted-file-input-temp-path]");
        safe = FILE_OUTPUT_TEMPORARY_PATH.matcher(safe).replaceAll("[redacted-file-output-temp-path]");
        safe = SPARK_TEMPORARY_PATH.matcher(safe).replaceAll("[redacted-spark-temp-path]");
        return URL_USER_INFO.matcher(safe).replaceAll("$1***@");
    }

    static String stackTrace(Throwable throwable) {
        return stackTrace(throwable, false);
    }

    static String spatialSafeStackTrace(Throwable throwable) {
        return stackTrace(throwable, containsSpatialThrowable(throwable));
    }

    /**
     * JDBC Driver exceptions can echo a custom option value, notably a session initialization
     * statement. Those values are valid task configuration, but are never safe Runner log data.
     */
    static String jdbcReadOptionSafeStackTrace(Throwable throwable, Collection<String> optionValues) {
        return stackTrace(throwable, containsSpatialThrowable(throwable), optionValues);
    }

    private static String stackTrace(Throwable throwable, boolean redactExceptionMessages) {
        return stackTrace(throwable, redactExceptionMessages, java.util.List.of());
    }

    private static String stackTrace(
            Throwable throwable,
            boolean redactExceptionMessages,
            Collection<String> valuesToRedact
    ) {
        StringWriter buffer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(buffer));
        String stack = buffer.toString();
        if (redactExceptionMessages) {
            stack = THROWABLE_MESSAGE_LINE.matcher(stack).replaceAll("$1: [spatial-message-redacted]");
        }
        String safe = sanitize(stack);
        if (valuesToRedact != null) {
            for (String value : valuesToRedact) {
                if (value != null && !value.isEmpty()) {
                    safe = safe.replace(value, "[redacted-jdbc-read-option-value]");
                }
            }
        }
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

    private static boolean containsSpatialThrowable(Throwable throwable) {
        java.util.Set<Throwable> visited = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<>());
        java.util.ArrayDeque<Throwable> pending = new java.util.ArrayDeque<>();
        pending.add(throwable);
        while (!pending.isEmpty()) {
            Throwable current = pending.removeFirst();
            if (!visited.add(current)) continue;
            String className = current.getClass().getName();
            if (isSpatialClass(className)) return true;
            for (StackTraceElement frame : current.getStackTrace()) {
                if (isSpatialClass(frame.getClassName())) return true;
            }
            if (current.getCause() != null) pending.addLast(current.getCause());
            for (Throwable suppressed : current.getSuppressed()) pending.addLast(suppressed);
        }
        return false;
    }

    private static boolean isSpatialClass(String className) {
        return className.startsWith("org.apache.sedona.")
                || className.startsWith("org.locationtech.jts.");
    }

    static String safeMessage(String value, String fallback) {
        String safe = value == null || value.isBlank() ? fallback : sanitize(value.trim());
        return safe.substring(0, Math.min(1000, safe.length()));
    }
}
