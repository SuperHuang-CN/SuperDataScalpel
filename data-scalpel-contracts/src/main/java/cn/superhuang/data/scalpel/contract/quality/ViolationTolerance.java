package cn.superhuang.data.scalpel.contract.quality;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@JsonClassDescription("质量规则允许的最大违规阈值；可按违规行数或占全部检查行数的百分比表达。")
public record ViolationTolerance(
        @JsonPropertyDescription("阈值单位：COUNT 表示违规行数，PERCENT 表示违规行数占目标模型全部检查行数的百分比。")
        @NotNull ViolationMetric metric,
        @JsonPropertyDescription("允许的最大违规值，实际值小于或等于它时规则通过；必须非负，COUNT 必须为整数，PERCENT 范围为 0 到 100。")
        @NotNull BigDecimal value
) {
}
