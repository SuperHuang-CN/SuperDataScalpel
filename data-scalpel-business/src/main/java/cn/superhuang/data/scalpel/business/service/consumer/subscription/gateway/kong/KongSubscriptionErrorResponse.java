package cn.superhuang.data.scalpel.business.service.consumer.subscription.gateway.kong;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record KongSubscriptionErrorResponse(String message, String name) {
}
