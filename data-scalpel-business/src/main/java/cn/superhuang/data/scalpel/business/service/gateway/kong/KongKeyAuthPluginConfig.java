package cn.superhuang.data.scalpel.business.service.gateway.kong;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

record KongKeyAuthPluginConfig(
        @JsonProperty("key_names") List<String> keyNames,
        @JsonProperty("hide_credentials") boolean hideCredentials,
        @JsonProperty("key_in_header") boolean keyInHeader,
        @JsonProperty("key_in_query") boolean keyInQuery,
        @JsonProperty("key_in_body") boolean keyInBody,
        @JsonProperty("run_on_preflight") boolean runOnPreflight
) {
}
