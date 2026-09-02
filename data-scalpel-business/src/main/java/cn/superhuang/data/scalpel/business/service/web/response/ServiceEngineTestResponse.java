package cn.superhuang.data.scalpel.business.service.web.response;

import cn.superhuang.data.scalpel.business.service.domain.ServiceEngineType;

import java.util.List;

public record ServiceEngineTestResponse(
        ServiceEngineType type,
        String code,
        String version,
        List<String> databaseTypes,
        List<String> capabilities,
        String normalizedAdminUrl,
        String normalizedRuntimeUrl,
        long elapsedMs
) {
    public ServiceEngineTestResponse(String code, List<String> databaseTypes, long elapsedMs) {
        this(
                ServiceEngineType.DATASCALPEL, code, null, databaseTypes,
                List.of(), null, null, elapsedMs
        );
    }
}
