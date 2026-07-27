package cn.superhuang.data.scalpel.business.service.consumer.credential.gateway.kong;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record KongCredentialErrorResponse(String message, String name) {
}
