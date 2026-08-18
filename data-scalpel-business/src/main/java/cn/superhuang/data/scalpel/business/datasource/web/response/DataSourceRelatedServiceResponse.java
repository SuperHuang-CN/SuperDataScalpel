package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.business.service.domain.DataServiceStatus;
import cn.superhuang.data.scalpel.contract.service.DataServiceType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DataSourceRelatedServiceResponse(
        UUID serviceId,
        String serviceCode,
        String serviceName,
        DataServiceType serviceType,
        DataServiceStatus status,
        Integer definitionVersion,
        List<DataSourceRelationKind> relationKinds,
        String routePath,
        Instant updatedAt
) {
}
