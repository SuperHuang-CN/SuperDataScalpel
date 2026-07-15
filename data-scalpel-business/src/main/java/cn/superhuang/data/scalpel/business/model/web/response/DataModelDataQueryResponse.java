package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.business.model.domain.DataModelField;

import java.util.List;
import java.util.Map;

/** Result of a read-only physical-table query issued through model management. */
public record DataModelDataQueryResponse(
        List<Column> columns,
        List<Map<String, Object>> rows,
        int pageNo,
        int pageSize,
        boolean hasNext,
        Long totalCount,
        boolean stableOrder
) {

    public record Column(String code, String name, String fieldType) {
        public static Column from(DataModelField field) {
            return new Column(field.getCode(), field.getName(), field.getFieldType().name());
        }
    }

    public DataModelDataQueryResponse {
        columns = List.copyOf(columns);
        rows = List.copyOf(rows);
    }
}
