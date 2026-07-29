package cn.superhuang.data.scalpel.contract.task;

public final class FileOutputPaths {
    private FileOutputPaths() {
    }

    public static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim().replaceAll("/{2,}", "/");
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
