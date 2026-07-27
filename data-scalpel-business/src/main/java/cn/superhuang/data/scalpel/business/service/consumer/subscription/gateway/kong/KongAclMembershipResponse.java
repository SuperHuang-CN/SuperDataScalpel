package cn.superhuang.data.scalpel.business.service.consumer.subscription.gateway.kong;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record KongAclMembershipResponse(
        String id,
        String group,
        List<String> tags,
        KongConsumerReference consumer
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    record KongConsumerReference(String id) {
    }
}
