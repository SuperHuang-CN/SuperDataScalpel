package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.business.model.domain.PhysicalTableMode;
import cn.superhuang.data.scalpel.business.model.service.ModelPhysicalTableInspection;
import cn.superhuang.data.scalpel.business.model.service.PhysicalTableState;

import java.util.List;

public record PhysicalTableInspectionResponse(
        PhysicalTableMode mode,
        PhysicalTableState state,
        String catalogName,
        String schemaName,
        String tableName,
        boolean exists,
        boolean compatible,
        boolean createSupported,
        String message,
        List<PhysicalTableDifferenceResponse> differences
) {
    public static PhysicalTableInspectionResponse from(PhysicalTableMode mode, ModelPhysicalTableInspection inspection) {
        return new PhysicalTableInspectionResponse(
                mode,
                inspection.state(),
                inspection.table().catalog(),
                inspection.table().schema(),
                inspection.table().table(),
                inspection.exists(),
                inspection.compatible(),
                inspection.createSupported(),
                inspection.message(),
                inspection.differences().stream().map(PhysicalTableDifferenceResponse::from).toList()
        );
    }
}
