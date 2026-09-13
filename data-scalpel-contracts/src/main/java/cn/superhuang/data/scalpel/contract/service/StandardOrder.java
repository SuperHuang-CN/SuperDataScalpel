package cn.superhuang.data.scalpel.contract.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record StandardOrder(
        @JsonPropertyDescription("服务定义中的字段编码。")
        @NotBlank String field,
        @JsonPropertyDescription("排序方向：ASC 升序或 DESC 降序。")
        @NotNull SortDirection direction
) {
}
