package cn.superhuang.data.scalpel.business.system.access.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "由管理员重置用户登录密码。")

public record ResetSystemUserPasswordRequest(
        @Schema(description = "新的登录密码；服务端只保存哈希且响应不返回明文。", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank @Size(min = 8, max = 128) String password
) {
}
