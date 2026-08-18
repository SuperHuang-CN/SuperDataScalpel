package cn.superhuang.data.scalpel.contract.quality;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ViolationTolerance(
        @NotNull ViolationMetric metric,
        @NotNull BigDecimal value
) {
}
