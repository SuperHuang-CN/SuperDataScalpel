package cn.superhuang.data.scalpel.business.service.consumer.gateway.kong;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

record KongConsumerRequest(
        String username,
        @JsonProperty("custom_id") String customId,
        List<String> tags
) {
}
