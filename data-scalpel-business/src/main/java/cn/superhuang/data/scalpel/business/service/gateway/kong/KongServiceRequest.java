package cn.superhuang.data.scalpel.business.service.gateway.kong;

import java.util.List;

record KongServiceRequest(
        String name,
        String url,
        List<String> tags
) {
}
