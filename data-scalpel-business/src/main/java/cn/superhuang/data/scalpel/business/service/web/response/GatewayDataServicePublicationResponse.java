package cn.superhuang.data.scalpel.business.service.web.response;

import cn.superhuang.data.scalpel.business.service.domain.DataServiceAccessMode;

import java.util.UUID;

/** Read model used to select currently published data services for gateway subscription operations. */
public record GatewayDataServicePublicationResponse(
        UUID dataServiceId,
        String dataServiceCode,
        String dataServiceName,
        String gatewayRoutePath,
        DataServiceAccessMode accessMode,
        String gatewayUrl
) {
}
