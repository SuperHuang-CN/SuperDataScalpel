package cn.superhuang.data.scalpel.business.metric.web.response;

import io.swagger.v3.oas.annotations.media.Schema;import cn.superhuang.data.scalpel.business.metric.domain.*;
import java.util.*;
import java.time.Instant;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
@Schema(description = "业务指标当前可编辑草稿、并发指纹、引用快照和发布健康状态。")
public record MetricDraftResponse(
        @Schema(description = "所属业务指标 UUID。")
        UUID metricId,
        @Schema(description = "当前保存但尚未发布的完整指标口径、结果绑定和参考资源。")
        MetricDefinition definition,
        @Schema(description = "当前已保存草稿 JSON 的 SHA-256 指纹；保存或发布时作为 expectedDraftFingerprint 提交，用于避免覆盖并发修改。")
        String fingerprint,
        @Schema(description = "当前定义和引用的健康检查结果。")
        MetricHealthResponse health,
        @Schema(description = "草稿中引用的模型、字段、指标和数据服务解析快照；没有引用时为空列表。")
        List<MetricReferenceSnapshot> references
) {}
