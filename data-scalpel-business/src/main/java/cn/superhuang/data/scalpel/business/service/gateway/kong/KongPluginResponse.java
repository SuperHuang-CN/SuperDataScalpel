package cn.superhuang.data.scalpel.business.service.gateway.kong;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
record KongPluginResponse(
        String id,
        String name,
        Map<String, Object> config,
        KongPluginService service,
        List<String> tags
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    record KongPluginService(String id) {
    }
}
