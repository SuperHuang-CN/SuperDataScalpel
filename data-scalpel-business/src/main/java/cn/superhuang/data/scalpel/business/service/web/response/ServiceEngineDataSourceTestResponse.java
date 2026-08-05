package cn.superhuang.data.scalpel.business.service.web.response;

import java.util.UUID;

public record ServiceEngineDataSourceTestResponse(
        UUID registrationId,
        String engineCode,
        UUID dataSourceId,
        String databaseType
) {
}
