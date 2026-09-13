package cn.superhuang.data.scalpel.business.service.accesslog.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "网关访问排行分组维度：按数据服务或按 API 消费者")

public enum GatewayAccessRankingDimension {
    SERVICE,
    CONSUMER
}
