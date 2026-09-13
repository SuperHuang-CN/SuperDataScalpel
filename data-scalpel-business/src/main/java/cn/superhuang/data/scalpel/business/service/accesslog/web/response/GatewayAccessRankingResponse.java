package cn.superhuang.data.scalpel.business.service.accesslog.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.service.accesslog.web.request.GatewayAccessRankingDimension;

import java.util.UUID;

@Schema(description = "按数据服务或 API 消费者聚合的一条网关访问排行结果。")

public record GatewayAccessRankingResponse(
        @Schema(description = "排行分组维度：SERVICE 按数据服务，CONSUMER 按 API 消费者。")
        GatewayAccessRankingDimension dimension,
        @Schema(description = "排行对象在 DataScalpel 中的 UUID；无法解析的聚合项为空。")
        UUID subjectId,
        @Schema(description = "排行对象的稳定编码。")
        String subjectCode,
        @Schema(description = "排行对象名称。")
        String subjectName,
        @Schema(description = "统计时间范围内该对象的网关请求总数。")
        long requestCount,
        @Schema(description = "HTTP 2xx 成功响应数量。")
        long status2xxCount,
        @Schema(description = "HTTP 4xx 客户端错误响应数量。")
        long status4xxCount,
        @Schema(description = "HTTP 5xx 服务端错误响应数量。")
        long status5xxCount,
        @Schema(description = "服务端错误请求数量，当前与 status5xxCount 相同，供 SERVER_ERROR_COUNT 排序指标使用。")
        long serverErrorCount,
        @Schema(description = "各小时请求延迟 P95 中的最大值，单位毫秒；没有可用样本时为空。")
        Double peakHourlyRequestLatencyP95Ms
) {
}
