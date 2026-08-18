package cn.superhuang.data.scalpel.business.model.web.response;

import java.util.List;

public record ModelMetadataImportModelResponse(
        String key,
        int rowNumber,
        String code,
        String name,
        String directoryPath,
        String warehouseLayerCode,
        ModelWarehouseLayerSummaryResponse warehouseLayer,
        String physicalTableName,
        List<String> clickHouseOrderByColumns,
        String description,
        boolean importable,
        List<String> issues,
        List<String> warnings,
        List<ModelMetadataImportFieldResponse> fields
) {

    public ModelMetadataImportModelResponse {
        clickHouseOrderByColumns = List.copyOf(clickHouseOrderByColumns);
        issues = List.copyOf(issues);
        warnings = List.copyOf(warnings);
        fields = List.copyOf(fields);
    }
}
