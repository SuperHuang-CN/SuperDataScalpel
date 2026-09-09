package cn.superhuang.data.scalpel.business.mcp.web.response;

import java.time.Instant;

public record McpInvocationOverviewResponse(Instant since, long total, long succeeded, long failed,
                                            double successRate, double averageDurationMillis) {}
