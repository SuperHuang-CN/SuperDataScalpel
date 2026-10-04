package cn.superhuang.data.scalpel.business.service.accesslog.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "最近十五分钟已入库调用的实时统计，与已完成小时汇总独立，不相加。异步入库存在延迟。")
public record GatewayAccessRecentResponse(
        @Schema(description = "日志接收是否在当前 Admin 配置中启用；false 时不会消费 Kafka。") boolean ingestionEnabled,
        @Schema(description = "统计起点，包含，UTC。") Instant from,
        @Schema(description = "统计终点，不包含，UTC。") Instant to,
        @Schema(description = "已接收调用数；零不代表链路健康。") long requestCount,
        @Schema(description = "HTTP 2xx 调用数。") long successCount,
        @Schema(description = "HTTP 4xx 调用数。") long clientErrorCount,
        @Schema(description = "HTTP 5xx 调用数。") long serverErrorCount,
        @Schema(description = "网关拒绝数，包括认证、授权和限流。") long rejectedCount,
        @Schema(description = "平均请求延迟，毫秒；无样本为空。") Double averageLatencyMs,
        @Schema(description = "当前窗口原始样本的真实 P95，毫秒；无样本为空。") Double p95LatencyMs,
        @Schema(description = "当前窗口原始样本的真实 P99，毫秒；无样本为空。") Double p99LatencyMs,
        @Schema(description = "当前筛选窗口内最后调用时间；无调用为空。") Instant lastRequestAt,
        @Schema(description = "当前筛选窗口内最近入库接收时间；无调用为空。") Instant lastReceivedAt
) {}
