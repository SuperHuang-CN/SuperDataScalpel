package cn.superhuang.data.scalpel.dialect.model;

public record ConnectionCheck(
        boolean success,
        String code,
        String message,
        long elapsedMs,
        String databaseProduct,
        String databaseVersion,
        String driverName
) {
}
