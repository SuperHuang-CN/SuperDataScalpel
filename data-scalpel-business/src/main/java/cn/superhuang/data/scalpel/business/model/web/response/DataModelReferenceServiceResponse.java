package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.business.service.domain.DataServiceStatus;
import cn.superhuang.data.scalpel.business.service.web.response.DataServiceRelatedModelRole;
import cn.superhuang.data.scalpel.contract.service.DataServiceType;

import java.util.UUID;

public record DataModelReferenceServiceResponse(
        UUID id,
        String name,
        DataServiceType type,
        DataServiceStatus status,
        DataServiceRelatedModelRole role,
        Integer ordinal
) {
}
