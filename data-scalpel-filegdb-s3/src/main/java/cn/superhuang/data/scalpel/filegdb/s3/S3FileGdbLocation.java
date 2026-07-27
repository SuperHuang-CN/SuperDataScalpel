package cn.superhuang.data.scalpel.filegdb.s3;

import java.util.Locale;

/** Location of an unpacked File Geodatabase stored below an S3 object prefix. */
public record S3FileGdbLocation(String bucket, String prefix) {
    public S3FileGdbLocation {
        bucket = requireBucket(bucket);
        prefix = normalizePrefix(prefix);
    }

    String objectKey(String fileName) {
        return prefix + "/" + fileName;
    }

    String safeLocation() {
        return "s3://" + bucket + "/" + prefix;
    }

    private static String requireBucket(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("S3 bucket must not be blank");
        }
        String normalized = value.strip();
        if (normalized.contains("/")
                || normalized.contains("\\")
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("S3 bucket contains invalid characters");
        }
        return normalized;
    }

    private static String normalizePrefix(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("S3 FileGDB prefix must not be blank");
        }
        String normalized = value.strip();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isBlank()
                || normalized.contains("\\")
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("S3 FileGDB prefix contains invalid characters");
        }
        String[] segments = normalized.split("/", -1);
        for (String segment : segments) {
            if (segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("S3 FileGDB prefix must not contain path traversal segments");
            }
        }
        String lastSegment = segments[segments.length - 1];
        if (!lastSegment.toLowerCase(Locale.ROOT).endsWith(".gdb")) {
            throw new IllegalArgumentException("S3 prefix must identify an unpacked .gdb directory");
        }
        return normalized;
    }
}
