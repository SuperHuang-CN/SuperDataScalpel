package cn.superhuang.data.scalpel.business.service.domain;

import java.util.UUID;

/** Stable Engine-private route derived from the immutable data-service identifier. */
public final class ServiceEngineRoutePath {

    private static final String PREFIX = "/runtime/v1/services/";

    private ServiceEngineRoutePath() {
    }

    public static String forService(UUID serviceId) {
        if (serviceId == null) throw new IllegalArgumentException("数据服务 ID 不能为空");
        return PREFIX + serviceId;
    }
}
