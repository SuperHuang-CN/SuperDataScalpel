package cn.superhuang.data.scalpel.business.service.gateway.kong;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

record KongRouteRequest(
        String name,
        List<String> paths,
        List<String> methods,
        List<String> protocols,
        @JsonProperty("strip_path") boolean stripPath,
        @JsonProperty("preserve_host") boolean preserveHost,
        List<String> tags
) {
}
