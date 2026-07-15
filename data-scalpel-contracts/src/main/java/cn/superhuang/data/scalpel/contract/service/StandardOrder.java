package cn.superhuang.data.scalpel.contract.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record StandardOrder(
        @NotBlank String column,
        @NotNull SortDirection direction
) {
}
