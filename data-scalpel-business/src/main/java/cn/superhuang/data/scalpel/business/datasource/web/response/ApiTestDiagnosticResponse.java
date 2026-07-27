package cn.superhuang.data.scalpel.business.datasource.web.response;

import java.util.List;

public record ApiTestDiagnosticResponse(
        String exceptionType,
        String rawMessage,
        Integer httpStatus,
        String responsePreview,
        List<ConnectionTestCauseResponse> causes
) {
    public ApiTestDiagnosticResponse {
        causes = causes == null ? List.of() : List.copyOf(causes);
    }
}
