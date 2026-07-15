package cn.superhuang.data.scalpel.business.model.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DataModelDataQueryOrderInput(
        @NotBlank String field,
        @NotNull DataModelDataQuerySortDirection direction
) {
}
