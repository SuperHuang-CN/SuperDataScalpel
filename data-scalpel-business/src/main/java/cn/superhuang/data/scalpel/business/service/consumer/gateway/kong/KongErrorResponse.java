package cn.superhuang.data.scalpel.business.service.consumer.gateway.kong;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record KongErrorResponse(String name, String message) {
}
