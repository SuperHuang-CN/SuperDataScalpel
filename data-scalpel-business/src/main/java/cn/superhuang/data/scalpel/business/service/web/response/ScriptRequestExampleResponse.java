package cn.superhuang.data.scalpel.business.service.web.response;

import java.util.List;

public record ScriptRequestExampleResponse(
        String id,
        String name,
        String bodyText,
        List<ScriptRequestParameterResponse> query,
        List<ScriptRequestParameterResponse> headers
) {
}
