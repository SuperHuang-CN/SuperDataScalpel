package cn.superhuang.data.scalpel.business.service.accesslog.web.response;

import java.time.Instant;
import java.util.List;

public record GatewayAccessTrendResponse(
        Instant fromInclusive,
        Instant toExclusive,
        List<GatewayAccessTrendPointResponse> points
) {
}
