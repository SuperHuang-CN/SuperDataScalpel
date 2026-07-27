package cn.superhuang.data.scalpel.business.service.consumer.credential.gateway.kong;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record KongKeyAuthCredentialResponse(
        String id,
        String key,
        List<String> tags,
        KongConsumerReference consumer
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    record KongConsumerReference(String id) {
    }
}
