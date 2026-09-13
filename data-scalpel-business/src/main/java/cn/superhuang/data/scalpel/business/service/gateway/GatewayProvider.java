package cn.superhuang.data.scalpel.business.service.gateway;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "API Gateway 提供方：NONE 表示未启用网关；KONG、APISIX 为对应网关；DATASCALPEL 表示同仓的 Super API Gateway。是否可用仍取决于部署配置和适配器。")
public enum GatewayProvider {
    NONE,
    KONG,
    APISIX,
    DATASCALPEL
}
