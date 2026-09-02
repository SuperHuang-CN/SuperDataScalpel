package cn.superhuang.data.scalpel.contract.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record StandardAggregator(
        @NotNull AggregateType function,
        @NotBlank String field,
        @NotBlank String alias
) {
}
