package cn.superhuang.data.scalpel.business.service.web.response;

import cn.superhuang.data.scalpel.business.model.domain.DataModelStatus;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;

import java.time.Instant;
import java.util.UUID;

public record SpatialDataServiceModelCandidateResponse(
        UUID id,
        String code,
        String name,
        DataModelStatus status,
        UUID dataSourceId,
        String dataSourceCode,
        String dataSourceName,
        String catalog,
        String schema,
        String table,
        String geometryColumn,
        GeometryKind geometryKind,
        Integer epsg,
        String primaryKeyColumn,
        boolean selectable,
        String unavailableReason,
        Instant updatedAt
) {
}
