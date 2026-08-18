package cn.superhuang.data.scalpel.business.datasource.web.response;

import java.util.UUID;

public record DataSourceTaskReferenceLocationResponse(
        DataSourceTaskRelationRole role,
        DataSourceRelationKind relationKind,
        DataSourceTaskResourceKind resourceKind,
        String locationKey,
        String locationLabel,
        String resourceLabel,
        UUID modelId,
        String modelName
) {
}
