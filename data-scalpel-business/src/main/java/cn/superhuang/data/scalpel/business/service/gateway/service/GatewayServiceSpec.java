package cn.superhuang.data.scalpel.business.service.gateway.service;

import cn.superhuang.data.scalpel.business.service.domain.DataServiceAccessMode;

import java.util.UUID;

public record GatewayServiceSpec(
        UUID id,
        String code,
        String name,
        long revision,
        String routePath,
        String upstreamUrl,
        DataServiceAccessMode accessMode
) {

    public GatewayServiceSpec(
            UUID id,
            String code,
            String name,
            long revision,
            String routePath,
            String upstreamUrl
    ) {
        this(id, code, name, revision, routePath, upstreamUrl, DataServiceAccessMode.PUBLIC);
    }

    public GatewayServiceSpec {
        if (id == null) throw new IllegalArgumentException("Data service ID is required");
        code = required(code, "Data service code");
        name = required(name, "Data service name");
        if (revision <= 0) throw new IllegalArgumentException("Data service revision must be positive");
        routePath = required(routePath, "Data service route path");
        upstreamUrl = required(upstreamUrl, "Service Engine public URL");
        accessMode = accessMode == null ? DataServiceAccessMode.PUBLIC : accessMode;
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        return value.trim();
    }
}
