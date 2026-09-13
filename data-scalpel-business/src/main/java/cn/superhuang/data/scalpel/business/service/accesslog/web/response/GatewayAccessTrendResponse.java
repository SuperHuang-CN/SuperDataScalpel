package cn.superhuang.data.scalpel.business.service.accesslog.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "指定统计窗口内按小时返回的 API 网关请求与延迟趋势。")

public record GatewayAccessTrendResponse(
        @Schema(description = "统计时间范围起点，包含该时刻。")
        Instant fromInclusive,
        @Schema(description = "统计时间范围终点，不包含该时刻。")
        Instant toExclusive,
        @Schema(description = "按 hourStart 升序排列的小时趋势点；无完整小时数据时为空列表。")
        List<GatewayAccessTrendPointResponse> points
) {
}
