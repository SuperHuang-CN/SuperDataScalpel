package cn.superhuang.data.scalpel.business.service.gateway.kong;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record KongRouteResponse(
        String id,
        String name,
        List<String> paths,
        List<String> methods,
        List<String> protocols,
        @JsonProperty("strip_path") boolean stripPath,
        @JsonProperty("preserve_host") boolean preserveHost,
        KongRouteService service,
        List<String> tags
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KongRouteService(String id) {
    }
}
