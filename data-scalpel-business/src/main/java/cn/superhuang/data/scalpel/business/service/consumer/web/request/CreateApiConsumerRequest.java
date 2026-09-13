package cn.superhuang.data.scalpel.business.service.consumer.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "创建用于订阅数据服务并持有 API 凭证的调用方。")

public record CreateApiConsumerRequest(
        @Schema(description = "调用方稳定技术编码，创建后不可修改。")
        @NotBlank(message = "消费者编码不能为空")
        @Size(max = 64, message = "消费者编码不能超过 64 个字符")
        @Pattern(
                regexp = "^[a-z][a-z0-9._-]{1,63}$",
                message = "消费者编码必须以小写字母开头，只能包含小写字母、数字、点、下划线和连字符，长度为 2 到 64 位"
        )
        String code,
        @Schema(description = "调用方显示名称。")
        @NotBlank(message = "消费者名称不能为空")
        @Size(max = 100, message = "消费者名称不能超过 100 个字符")
        String name,
        @Schema(description = "调用方用途、所属系统或负责人说明；未填写时为空。")
        @Size(max = 1000, message = "消费者说明不能超过 1000 个字符")
        String description
) {
}
