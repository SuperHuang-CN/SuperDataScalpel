package cn.superhuang.data.scalpel.contract.service;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/** A single top-level WHERE condition. Values are converted by the published field type. */
public record StandardFilter(
        @NotBlank String name,
        @NotBlank String operator,
        Object value,
        Object secondValue,
        List<Object> values
) {

    public StandardFilter {
        values = values == null ? List.of() : List.copyOf(values);
    }
}
