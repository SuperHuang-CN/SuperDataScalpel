package cn.superhuang.data.scalpel.business.service.accesslog.web.resource;

import cn.superhuang.data.scalpel.business.service.accesslog.service.GatewayAccessQueryService;
import cn.superhuang.data.scalpel.business.service.accesslog.web.response.GatewayAccessLogResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
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

    @GetMapping
    @PreAuthorize("hasAuthority('service.view')")
    @Operation(summary = "查询最近七天的网关访问明细")
    public PageResponse<GatewayAccessLogResponse> search(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to,
            @RequestParam(required = false) UUID dataServiceId,
            @RequestParam(required = false) UUID consumerId,
            @RequestParam(required = false) Integer responseStatus,
            @RequestParam(required = false) String gatewayRequestId,
            @RequestParam(defaultValue = "false") boolean abnormalOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
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
