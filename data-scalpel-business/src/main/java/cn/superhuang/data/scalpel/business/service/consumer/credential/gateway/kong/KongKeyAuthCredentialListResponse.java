package cn.superhuang.data.scalpel.business.service.consumer.credential.gateway.kong;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record KongKeyAuthCredentialListResponse(List<KongKeyAuthCredentialResponse> data) {
}
