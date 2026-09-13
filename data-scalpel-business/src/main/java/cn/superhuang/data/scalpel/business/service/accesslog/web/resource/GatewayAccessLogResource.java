package cn.superhuang.data.scalpel.business.service.accesslog.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.service.accesslog.service.GatewayAccessQueryService;
import cn.superhuang.data.scalpel.business.service.accesslog.web.response.GatewayAccessLogResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/gateway-access-logs")
@Tag(name = "数据服务-网关访问日志")
public class GatewayAccessLogResource {

    private final GatewayAccessQueryService queryService;

    public GatewayAccessLogResource(GatewayAccessQueryService queryService) {
        this.queryService = queryService;
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询最近七天的网关访问明细")
    @GetMapping
    @PreAuthorize("hasAuthority('service.view')")
    @Operation(summary = "查询网关访问明细", description = "分页查询原始网关访问事件，支持按服务、消费者、响应状态、请求标识和异常状态筛选。默认最近 1 小时，范围上限为原始日志保留期（默认 7 天），页码从 0 开始。")
    public PageResponse<GatewayAccessLogResponse> search(
            @Parameter(description = "查询时间范围起点，包含该时刻；为空时默认为终点前 1 小时。")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,
            @Parameter(description = "查询时间范围终点，不包含该时刻；为空时使用当前时间。")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to,
            @Parameter(description = "数据服务 UUID。") @RequestParam(required = false) UUID dataServiceId,
            @Parameter(description = "API 调用方 UUID。") @RequestParam(required = false) UUID consumerId,
            @Parameter(description = "可选 HTTP 响应状态筛选。") @RequestParam(required = false) Integer responseStatus,
            @Parameter(description = "网关请求标识。") @RequestParam(required = false) String gatewayRequestId,
            @Parameter(description = "是否只返回异常运行记录。") @RequestParam(defaultValue = "false") boolean abnormalOnly,
            @Parameter(description = "页码，从 0 开始。") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页记录数，范围 1～200，默认 20。") @RequestParam(defaultValue = "20") int size
    ) {
        return queryService.searchLogs(
                from,
                to,
                dataServiceId,
                consumerId,
                responseStatus,
                gatewayRequestId,
                abnormalOnly,
                page,
                size
        );
    }
}
