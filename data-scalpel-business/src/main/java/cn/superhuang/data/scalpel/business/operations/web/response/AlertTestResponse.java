package cn.superhuang.data.scalpel.business.operations.web.response;

import io.swagger.v3.oas.annotations.media.Schema;import java.util.UUID;
@Schema(description = "测试告警已受理后的异步投递标识。")
public record AlertTestResponse(
        @Schema(description = "本次测试创建的告警投递 UUID，可用于查询最终发送结果。")
        UUID deliveryId
) {}
