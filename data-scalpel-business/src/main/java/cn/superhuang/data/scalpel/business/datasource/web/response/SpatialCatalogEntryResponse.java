package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.business.datasource.domain.SpatialServiceProtocol;

public record SpatialCatalogEntryResponse(
        SpatialServiceProtocol protocol,
        String remoteIdentifier,
        String name,
        String title,
        String kind,
        boolean queryable,
        Integer epsgCode
) {
}
