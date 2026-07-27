package cn.superhuang.data.scalpel.business.datasource.web.response;

import java.util.List;

public record ConnectionTestDiagnosticResponse(
        String exceptionType,
        String rawMessage,
        String sqlState,
        Integer vendorCode,
        Integer httpStatus,
        String responsePreview,
        List<ConnectionTestCauseResponse> causes
) {
    public ConnectionTestDiagnosticResponse {
        causes = causes == null ? List.of() : List.copyOf(causes);
    }
}
