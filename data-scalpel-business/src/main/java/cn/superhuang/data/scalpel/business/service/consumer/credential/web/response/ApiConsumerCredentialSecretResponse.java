package cn.superhuang.data.scalpel.business.service.consumer.credential.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "创建或轮换 API 调用方凭证后的结果；只有当前修订成功同步到活动网关时才单次返回明文密钥。")

public record ApiConsumerCredentialSecretResponse(
        @Schema(description = "新创建或轮换后的凭证元数据。")
        ApiConsumerCredentialResponse credential,
        @Schema(description = "完整 API Key；网关同步成功时只在本次响应中返回，应立即安全保存。同步失败、操作被较新修订取代或结果不再是当前状态时为空，数据库无法恢复该明文。")
        String secret
) {
}
