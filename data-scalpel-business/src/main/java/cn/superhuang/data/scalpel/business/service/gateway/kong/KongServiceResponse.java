package cn.superhuang.data.scalpel.business.service.gateway.kong;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record KongServiceResponse(
        String id,
        String name,
        String url,
        String protocol,
        String host,
        Integer port,
        String path,
        List<String> tags
) {
}
