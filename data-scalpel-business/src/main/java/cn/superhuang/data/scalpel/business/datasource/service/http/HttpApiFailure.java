package cn.superhuang.data.scalpel.business.datasource.service.http;

public record HttpApiFailure(
        String code,
        String message,
        Integer httpStatus,
        String responsePreview,
        Throwable cause
) {
}
