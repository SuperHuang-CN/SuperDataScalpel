package cn.superhuang.data.scalpel.business.service.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceAccessMode;
import cn.superhuang.data.scalpel.business.service.gateway.domain.GatewayServiceBinding;
import cn.superhuang.data.scalpel.business.service.gateway.domain.GatewayServicePublicationStatus;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayReconciliationReason;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayReconciliationStatus;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "一次数据服务网关发布绑定，包含远端对象、访问方式、发布修订和对账状态。")

public record GatewayServiceBindingResponse(
        @Schema(description = "网关绑定 UUID，用于定位本次发布关系。")
        UUID id,
        @Schema(description = "网关提供方；当前发布绑定通常为 DATASCALPEL，NONE 不会形成绑定。")
        GatewayProvider provider,
        @Schema(description = "网关侧服务对象标识；发布尚未成功或远端对象缺失时为空。")
        String externalServiceId,
        @Schema(description = "网关侧路由对象标识；发布尚未成功或远端对象缺失时为空。")
        String externalRouteId,
        @Schema(description = "本地期望网关暴露的路由路径；发布开始后即有值，PUBLISH_FAILED 不表示远端一定不存在该路径。")
        String gatewayRoutePath,
        @Schema(description = "发布到网关的访问模式：PUBLIC 匿名访问，SUBSCRIPTION_REQUIRED 订阅鉴权。")
        DataServiceAccessMode accessMode,
        @Schema(description = "最近一次成功发布时确认应用的数据服务修订号；初始为 0。仅 publicationStatus=PUBLISHED 时可视为当前有效，小于服务 revision 表示网关快照落后。")
        long publishedRevision,
        @Schema(description = "网关发布状态：PUBLISHING、PUBLISHED、PUBLISH_FAILED、REMOVING 或 REMOVE_FAILED。")
        GatewayServicePublicationStatus publicationStatus,
        @Schema(description = "最近一次成功发布返回的完整网关调用 URL；从未成功发布时为空。发布或撤回失败后该历史值可能仍保留，需结合 publicationStatus 判断是否当前有效。")
        String gatewayUrl,
        @Schema(description = "最近一次安全错误摘要；没有错误时为空。")
        String lastError,
        @Schema(description = "当前发布或撤回操作开始时间，ISO-8601 UTC 时间戳；仅 PUBLISHING 或 REMOVING 时有值，进入终态后为空。")
        Instant operationStartedAt,
        @Schema(description = "最近一次成功发布时间，ISO-8601 UTC 时间戳；从未成功发布时为空。后续失败不会清除此历史时间。")
        Instant publishedAt,
        @Schema(description = "最近一次显式对账状态：未检查、检查中、一致、漂移或检查失败；对账不会自动覆盖远端。")
        GatewayReconciliationStatus reconciliationStatus,
        @Schema(description = "发现漂移时的稳定原因，例如远端缺失、配置不一致、归属不一致或本地绑定缺失。")
        GatewayReconciliationReason reconciliationReason,
        @Schema(description = "最近一次网关对账的安全结果说明。")
        String reconciliationMessage,
        @Schema(description = "当前对账操作 UUID，用于防止较早检查结果覆盖较新的检查；仅 reconciliationStatus=CHECKING 时有值。")
        UUID reconciliationOperationId,
        @Schema(description = "当前对账开始时间，ISO-8601 UTC 时间戳；仅 reconciliationStatus=CHECKING 时有值。")
        Instant reconciliationStartedAt,
        @Schema(description = "最近一次完成或失败的对账时间，ISO-8601 UTC 时间戳；从未完成对账时为空。")
        Instant lastReconciledAt,
        @Schema(description = "创建时间，ISO-8601 UTC 时间戳。")
        Instant createdAt,
        @Schema(description = "最后更新时间，ISO-8601 UTC 时间戳。")
        Instant updatedAt
) {

    public static GatewayServiceBindingResponse from(GatewayServiceBinding binding) {
        return new GatewayServiceBindingResponse(
                binding.getId(),
                binding.getProvider(),
                binding.getExternalServiceId(),
                binding.getExternalRouteId(),
                binding.getGatewayRoutePath(),
                binding.getAccessMode(),
                binding.getPublishedRevision(),
                binding.getPublicationStatus(),
                binding.getGatewayUrl(),
                binding.getLastError(),
                binding.getOperationStartedAt(),
                binding.getPublishedAt(),
                binding.getReconciliationStatus(),
                binding.getReconciliationReason(),
                binding.getReconciliationMessage(),
                binding.getReconciliationOperationId(),
                binding.getReconciliationStartedAt(),
                binding.getLastReconciledAt(),
                binding.getCreatedAt(),
                binding.getUpdatedAt()
        );
    }
}
