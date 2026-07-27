package cn.superhuang.data.scalpel.business.service.consumer.gateway.kong;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record KongConsumerResponse(
        String id,
        String username,
        @JsonProperty("custom_id") String customId,
        List<String> tags
) {
}
