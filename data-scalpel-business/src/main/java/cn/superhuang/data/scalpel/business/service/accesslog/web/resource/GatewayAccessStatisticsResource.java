package cn.superhuang.data.scalpel.business.service.accesslog.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.service.accesslog.service.GatewayAccessQueryService;
import cn.superhuang.data.scalpel.business.service.accesslog.web.request.GatewayAccessRankingDimension;
import cn.superhuang.data.scalpel.business.service.accesslog.web.request.GatewayAccessRankingMetric;
import cn.superhuang.data.scalpel.business.service.accesslog.web.response.GatewayAccessHourlyStatResponse;
import cn.superhuang.data.scalpel.business.service.accesslog.web.response.GatewayAccessOverviewResponse;
import cn.superhuang.data.scalpel.business.service.accesslog.web.response.GatewayAccessRankingResponse;
import cn.superhuang.data.scalpel.business.service.accesslog.web.response.GatewayAccessTrendResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/gateway-access-statistics")
@Tag(name = "数据服务-网关访问统计")
public class GatewayAccessStatisticsResource {

    private final GatewayAccessQueryService queryService;

    public GatewayAccessStatisticsResource(GatewayAccessQueryService queryService) {
        this.queryService = queryService;
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询网关访问统计概览")
    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('service.view')")
    @Operation(summary = "查询网关访问统计概览", description = "汇总已完成小时桶中的请求数、状态码、拒绝和错误、流量及延迟指标。默认最近 24 个完整小时，范围上限为小时汇总保留期（默认 180 天）。")
    public GatewayAccessOverviewResponse overview(
            @Parameter(description = "统计时间范围起点，包含该时刻；为空时使用系统默认时间窗。")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,
            @Parameter(description = "统计时间范围终点，不包含该时刻；为空时使用当前时间。")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to,
            @Parameter(description = "数据服务 UUID。") @RequestParam(required = false) UUID dataServiceId,
            @Parameter(description = "API 调用方 UUID。") @RequestParam(required = false) UUID consumerId
    ) {
        return queryService.overview(from, to, dataServiceId, consumerId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询当前服务和消费者范围的小时访问趋势")
    @GetMapping("/hourly")
    @PreAuthorize("hasAuthority('service.view')")
    @Operation(summary = "查询网关小时访问趋势", description = "按小时返回当前服务和消费者筛选范围内的汇总指标；未指定消费者时读取服务维度汇总，指定消费者时读取消费者与服务联合维度。")
    public GatewayAccessTrendResponse hourly(
            @Parameter(description = "统计时间范围起点，包含该时刻；为空时使用系统默认时间窗。")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,
            @Parameter(description = "统计时间范围终点，不包含该时刻；为空时使用当前时间。")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to,
            @Parameter(description = "数据服务 UUID。") @RequestParam(required = false) UUID dataServiceId,
            @Parameter(description = "API 调用方 UUID。") @RequestParam(required = false) UUID consumerId
    ) {
        return queryService.hourlyTrend(from, to, dataServiceId, consumerId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询数据服务小时访问趋势")
    @GetMapping("/services/hourly")
    @PreAuthorize("hasAuthority('service.view')")
    @Operation(summary = "查询数据服务小时访问趋势", description = "返回指定数据服务按小时、网关提供方拆分的访问统计。默认最近 24 个完整小时，只读取已归档的小时桶。")
    public List<GatewayAccessHourlyStatResponse> serviceHourly(
            @Parameter(description = "统计时间范围起点，包含该时刻；为空时使用系统默认时间窗。")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,
            @Parameter(description = "统计时间范围终点，不包含该时刻；为空时使用当前时间。")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to,
            @Parameter(description = "数据服务 UUID。") @RequestParam UUID dataServiceId
    ) {
        return queryService.serviceHourly(from, to, dataServiceId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询 API 消费者小时访问趋势")
    @GetMapping("/consumers/hourly")
    @PreAuthorize("hasAuthority('service.view')")
    @Operation(summary = "查询 API 消费者小时访问趋势", description = "返回指定消费者按小时、数据服务和网关提供方拆分的访问统计，可进一步限定数据服务。匿名请求不会归入消费者统计。")
    public List<GatewayAccessHourlyStatResponse> consumerHourly(
            @Parameter(description = "统计时间范围起点，包含该时刻；为空时使用系统默认时间窗。")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,
            @Parameter(description = "统计时间范围终点，不包含该时刻；为空时使用当前时间。")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to,
            @Parameter(description = "API 调用方 UUID。") @RequestParam UUID consumerId,
            @Parameter(description = "数据服务 UUID。") @RequestParam(required = false) UUID dataServiceId
    ) {
        return queryService.consumerHourly(from, to, consumerId, dataServiceId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询数据服务或消费者调用排行")
    @GetMapping("/rankings")
    @PreAuthorize("hasAuthority('service.view')")
    @Operation(summary = "查询数据服务或消费者调用排行", description = "在指定完整小时范围内，按数据服务或消费者计算请求量、服务端错误数或峰值 P95 延迟排行；可用另一维度作过滤，limit 受服务端上限约束。")
    public List<GatewayAccessRankingResponse> rankings(
            @Parameter(description = "统计时间范围起点，包含该时刻；为空时使用系统默认时间窗。")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,
            @Parameter(description = "统计时间范围终点，不包含该时刻；为空时使用当前时间。")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to,
            @Parameter(description = "排行主体：SERVICE 按数据服务，CONSUMER 按 API 消费者。") @RequestParam(defaultValue = "SERVICE") GatewayAccessRankingDimension dimension,
            @Parameter(description = "排序指标：REQUEST_COUNT 请求量、SERVER_ERROR_COUNT 服务端错误数、P95_LATENCY 峰值 P95 延迟。") @RequestParam(defaultValue = "REQUEST_COUNT") GatewayAccessRankingMetric metric,
            @Parameter(description = "数据服务 UUID。") @RequestParam(required = false) UUID dataServiceId,
            @Parameter(description = "API 调用方 UUID。") @RequestParam(required = false) UUID consumerId,
            @Parameter(description = "最多返回的记录数。") @RequestParam(defaultValue = "20") int limit
    ) {
        return queryService.rankings(
                from,
                to,
                dimension,
                metric,
                dataServiceId,
                consumerId,
                limit
        );
    }
}
