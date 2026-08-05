package cn.superhuang.data.scalpel.business.service.web.response;

import java.util.List;
import java.util.UUID;

public record ScriptDataServiceDefinitionResponse(
        UUID dataSourceId,
        String script,
        List<ScriptRequestExampleResponse> examples,
        int version
) {
}
