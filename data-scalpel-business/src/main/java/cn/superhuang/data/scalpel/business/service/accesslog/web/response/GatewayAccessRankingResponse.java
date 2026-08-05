package cn.superhuang.data.scalpel.business.service.accesslog.web.response;

import cn.superhuang.data.scalpel.business.service.accesslog.web.request.GatewayAccessRankingDimension;

import java.util.UUID;

public record GatewayAccessRankingResponse(
        GatewayAccessRankingDimension dimension,
        UUID subjectId,
        String subjectCode,
        String subjectName,
        long requestCount,
        long status2xxCount,
        long status4xxCount,
        long status5xxCount,
        long serverErrorCount,
        Double peakHourlyRequestLatencyP95Ms
) {
}
