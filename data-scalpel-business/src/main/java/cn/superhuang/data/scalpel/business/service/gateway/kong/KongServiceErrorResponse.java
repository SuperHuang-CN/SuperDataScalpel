package cn.superhuang.data.scalpel.business.service.gateway.kong;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record KongServiceErrorResponse(
        String name,
        String message
) {
}
