package cn.superhuang.data.scalpel.business.task.web.request;

import cn.superhuang.data.scalpel.contract.task.MaskingRuleDefinition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateDataMaskingRuleRequest(
        @NotBlank
        @Size(max = 64)
        @Pattern(
                regexp = "[a-z0-9_]+",
                message = "规则编码只能包含小写字母、数字和下划线"
        )
        String code,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 1000) String description,
        @NotNull MaskingRuleDefinition definition
) {
}
