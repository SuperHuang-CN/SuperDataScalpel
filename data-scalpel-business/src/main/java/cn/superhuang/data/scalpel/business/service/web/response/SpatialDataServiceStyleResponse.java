package cn.superhuang.data.scalpel.business.service.web.response;

import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument;
import cn.superhuang.data.scalpel.business.service.domain.SpatialGeometryFamily;
import cn.superhuang.data.scalpel.business.service.domain.SpatialStyleMode;
import cn.superhuang.data.scalpel.business.service.domain.SpatialStyleSyncStatus;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;

import java.time.Instant;
import java.util.List;

public record SpatialDataServiceStyleResponse(
        SpatialStyleMode mode,
        GeometryKind geometryKind,
        SpatialGeometryFamily geometryFamily,
        boolean simpleEditable,
        SpatialStyleDocument styleDocument,
        SpatialStyleDocument defaultStyleDocument,
        List<SpatialStyleFieldResponse> fields,
        String sldFileName,
        Integer sldFileSize,
        String uploadedSldText,
        int styleVersion,
        Integer appliedStyleVersion,
        SpatialStyleSyncStatus syncStatus,
        String syncError,
        Instant appliedAt,
        boolean deployed
) {
}
