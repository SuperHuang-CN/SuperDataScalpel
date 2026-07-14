package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.dialect.model.ConnectionCheck;

public record ConnectionTestResponse(
        boolean success,
        String code,
        String message,
        long elapsedMs,
        String databaseProduct,
        String databaseVersion,
        String driverName
) {
    public static ConnectionTestResponse from(ConnectionCheck check) {
        return new ConnectionTestResponse(
                check.success(),
                check.code(),
                check.message(),
                check.elapsedMs(),
                check.databaseProduct(),
                check.databaseVersion(),
                check.driverName()
        );
    }
}
