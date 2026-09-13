package cn.superhuang.data.scalpel.business.service.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceAccessMode;

import java.util.UUID;

/** Read model used to select currently published data services for gateway subscription operations. */
@Schema(description = "按指定访问模式查询的本地已确认网关发布摘要；只包含 publicationStatus=PUBLISHED 的绑定，不实时访问网关或验证对账状态。")
public record GatewayDataServicePublicationResponse(
        @Schema(description = "已发布到网关的数据服务 UUID。")
        UUID dataServiceId,
        @Schema(description = "数据服务稳定编码。")
        String dataServiceCode,
        @Schema(description = "数据服务名称。")
        String dataServiceName,
        @Schema(description = "网关暴露的 /open-api/v1/ 路由路径；gatewayUrl 已是完整调用地址，无需再次拼接。")
        String gatewayRoutePath,
        @Schema(description = "网关访问模式：PUBLIC 或 SUBSCRIPTION_REQUIRED；只有后者需要创建订阅。")
        DataServiceAccessMode accessMode,
        @Schema(description = "当前发布的完整网关调用 URL。")
        String gatewayUrl
) {
}
