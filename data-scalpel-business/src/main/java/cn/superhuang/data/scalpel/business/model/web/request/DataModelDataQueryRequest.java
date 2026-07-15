package cn.superhuang.data.scalpel.business.model.web.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/**
 * SQL-free conditional query for one model's resolved physical table.
 * Grouping and aggregation intentionally remain outside the first model-preview release.
 */
public record DataModelDataQueryRequest(
        @Min(1) Integer pageNo,
        @Min(1) Integer pageSize,
        @Pattern(regexp = "AND|OR") String conditionType,
        List<String> columns,
        List<@Valid DataModelDataQueryFilterInput> filters,
        List<@Valid DataModelDataQueryOrderInput> orders,
        Boolean returnCount
) {

    public DataModelDataQueryRequest {
        columns = columns == null ? List.of() : List.copyOf(columns);
        filters = filters == null ? List.of() : List.copyOf(filters);
        orders = orders == null ? List.of() : List.copyOf(orders);
    }
}
