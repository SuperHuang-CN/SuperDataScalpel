package cn.superhuang.data.scalpel.business.datasource.service.http;

public record ConnectionProbeResult(
        boolean success,
        String code,
        String message,
        long elapsedMs,
        Integer httpStatus,
        String contentType,
        HttpApiFailure failure
) {
}
