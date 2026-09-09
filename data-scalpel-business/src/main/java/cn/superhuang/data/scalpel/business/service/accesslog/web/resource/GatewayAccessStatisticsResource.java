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
    @Operation(summary = "查询网关访问统计概览")
    public GatewayAccessOverviewResponse overview(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to,
            @RequestParam(required = false) UUID dataServiceId,
            @RequestParam(required = false) UUID consumerId
    ) {
        return queryService.overview(from, to, dataServiceId, consumerId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询当前服务和消费者范围的小时访问趋势")
    @GetMapping("/hourly")
    @PreAuthorize("hasAuthority('service.view')")
    @Operation(summary = "查询当前服务和消费者范围的小时访问趋势")
    public GatewayAccessTrendResponse hourly(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to,
            @RequestParam(required = false) UUID dataServiceId,
            @RequestParam(required = false) UUID consumerId
    ) {
        return queryService.hourlyTrend(from, to, dataServiceId, consumerId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询数据服务小时访问趋势")
    @GetMapping("/services/hourly")
    @PreAuthorize("hasAuthority('service.view')")
    @Operation(summary = "查询数据服务小时访问趋势")
    public List<GatewayAccessHourlyStatResponse> serviceHourly(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to,
            @RequestParam UUID dataServiceId
    ) {
        return queryService.serviceHourly(from, to, dataServiceId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询 API 消费者小时访问趋势")
    @GetMapping("/consumers/hourly")
    @PreAuthorize("hasAuthority('service.view')")
    @Operation(summary = "查询 API 消费者小时访问趋势")
    public List<GatewayAccessHourlyStatResponse> consumerHourly(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to,
            @RequestParam UUID consumerId,
            @RequestParam(required = false) UUID dataServiceId
    ) {
        return queryService.consumerHourly(from, to, consumerId, dataServiceId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询数据服务或消费者调用排行")
    @GetMapping("/rankings")
    @PreAuthorize("hasAuthority('service.view')")
    @Operation(summary = "查询数据服务或消费者调用排行")
    public List<GatewayAccessRankingResponse> rankings(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to,
            @RequestParam(defaultValue = "SERVICE") GatewayAccessRankingDimension dimension,
            @RequestParam(defaultValue = "REQUEST_COUNT") GatewayAccessRankingMetric metric,
            @RequestParam(required = false) UUID dataServiceId,
            @RequestParam(required = false) UUID consumerId,
            @RequestParam(defaultValue = "20") int limit
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
