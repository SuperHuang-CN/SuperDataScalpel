package cn.superhuang.data.scalpel.business.model.web.request;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/** One typed filter used by the model physical-table data query. */
public record DataModelDataQueryFilterInput(
        @NotBlank String field,
        @NotBlank String operator,
        Object value,
        Object secondValue,
        List<Object> values
) {

    public DataModelDataQueryFilterInput {
        values = values == null ? List.of() : List.copyOf(values);
    }
}
