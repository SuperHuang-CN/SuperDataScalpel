package cn.superhuang.data.scalpel.business.service.consumer.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateApiConsumerRequest(
        @NotBlank(message = "消费者名称不能为空")
        @Size(max = 100, message = "消费者名称不能超过 100 个字符")
        String name,

        @Size(max = 1000, message = "消费者说明不能超过 1000 个字符")
        String description
) {
}
