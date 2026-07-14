package cn.superhuang.data.scalpel.business.system.configuration.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for updating the only editable property of a system configuration. */
public record UpdateSystemConfigurationRequest(
        @NotBlank(message = "配置值不能为空")
        @Size(max = 4000, message = "配置值不能超过 4000 个字符")
        String configValue
) {
}
