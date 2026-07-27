package cn.superhuang.data.scalpel.business.service.gateway.kong;

import java.util.UUID;

/**
 * Stable Kong-only names shared by the service publication and subscription adapters.
 */
public final class KongGatewayManagedNames {

    private KongGatewayManagedNames() {
    }

    public static String serviceAclGroup(UUID dataServiceId) {
        if (dataServiceId == null) throw new IllegalArgumentException("Data service ID is required");
        return "datascalpel-service-" + dataServiceId;
    }
}
