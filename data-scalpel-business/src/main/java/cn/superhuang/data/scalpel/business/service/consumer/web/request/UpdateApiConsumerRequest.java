package cn.superhuang.data.scalpel.business.service.consumer.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "修改 API 调用方的显示信息；稳定编码和已有凭证不受影响。")

public record UpdateApiConsumerRequest(
        @Schema(description = "调用方显示名称。")
        @NotBlank(message = "消费者名称不能为空")
        @Size(max = 100, message = "消费者名称不能超过 100 个字符")
        String name,
        @Schema(description = "调用方用途、所属系统或负责人说明；传空值表示清除。")
        @Size(max = 1000, message = "消费者说明不能超过 1000 个字符")
        String description
) {
}
