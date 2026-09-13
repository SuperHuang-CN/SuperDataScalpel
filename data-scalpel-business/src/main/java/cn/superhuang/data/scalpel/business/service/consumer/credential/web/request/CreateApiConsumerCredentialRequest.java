package cn.superhuang.data.scalpel.business.service.consumer.credential.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "为指定 API 调用方创建一把新的独立 API Key。")

public record CreateApiConsumerCredentialRequest(
        @Schema(description = "便于管理员区分用途的凭证显示名称。")
        @NotBlank @Size(max = 100) String name
) {
}
