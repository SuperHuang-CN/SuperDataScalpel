package cn.superhuang.data.scalpel.business.service.accesslog.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "网关访问排行指标：请求总数、服务端错误数或 P95 延迟")

public enum GatewayAccessRankingMetric {
    REQUEST_COUNT,
    SERVER_ERROR_COUNT,
    P95_LATENCY
}
