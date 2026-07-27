package cn.superhuang.data.scalpel.business.service.gateway.kong;

import java.util.List;

record KongPluginRequest(
        String name,
        Object config,
        List<String> tags
) {
}
