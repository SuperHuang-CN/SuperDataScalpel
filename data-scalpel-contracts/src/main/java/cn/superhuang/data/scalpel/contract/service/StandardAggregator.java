package cn.superhuang.data.scalpel.contract.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record StandardAggregator(
        @JsonPropertyDescription("受控聚合函数，例如 COUNT、SUM、AVG、MIN 或 MAX。")
        @NotNull AggregateType function,
        @JsonPropertyDescription("服务定义中的字段编码。")
        @NotBlank String field,
        @JsonPropertyDescription("输出字段别名；为空时使用表达式默认名称。")
        @NotBlank String alias
) {
}
