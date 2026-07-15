package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;

import java.util.List;
import java.util.Map;

public record DataModelPreviewResponse(
        String catalogName,
        String schemaName,
        String tableName,
        List<Column> columns,
        List<Map<String, Object>> rows,
        int limit,
        boolean truncated
) {
    public record Column(String code, String name, String fieldType) {
    }

    public static DataModelPreviewResponse from(
            DataModel model,
            List<DataModelField> fields,
            List<Map<String, Object>> rows,
            int limit,
            boolean truncated
    ) {
        return new DataModelPreviewResponse(
                model.getCatalogName(),
                model.getSchemaName(), model.getPhysicalTableName(),
                fields.stream().map(field -> new Column(field.getCode(), field.getName(), field.getFieldType().name())).toList(),
                rows, limit, truncated
        );
    }
}
