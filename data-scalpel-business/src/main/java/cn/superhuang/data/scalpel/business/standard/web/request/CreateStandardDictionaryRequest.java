package cn.superhuang.data.scalpel.business.standard.web.request;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateStandardDictionaryRequest(
        @NotBlank
        @Pattern(
                regexp = "[A-Za-z][A-Za-z0-9_]{0,63}",
                message = "码表编码只能包含字母、数字和下划线，且必须以字母开头"
        )
        String code,
        @NotBlank @Size(max = 100) String name,
        @NotNull PlatformDataType valueType,
        @Size(max = 500) String description
) {
}
