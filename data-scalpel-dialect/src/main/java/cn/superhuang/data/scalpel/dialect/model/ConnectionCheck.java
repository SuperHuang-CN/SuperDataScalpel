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
    public static ConnectionCheck failed(String code, String message, long elapsedMs) {
        return new ConnectionCheck(false, code, message, elapsedMs, null, null, null);
    }
}
