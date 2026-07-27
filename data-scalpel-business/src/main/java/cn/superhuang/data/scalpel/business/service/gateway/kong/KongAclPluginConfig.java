package cn.superhuang.data.scalpel.business.service.gateway.kong;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

record KongAclPluginConfig(
        List<String> allow,
        List<String> deny,
        @JsonProperty("hide_groups_header") boolean hideGroupsHeader,
        @JsonProperty("always_use_authenticated_groups") boolean alwaysUseAuthenticatedGroups
) {
}
