package cn.superhuang.data.scalpel.business.metric.web.response;

import io.swagger.v3.oas.annotations.media.Schema;import cn.superhuang.data.scalpel.business.metric.domain.*;
import java.util.*;
import java.time.Instant;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
@Schema(description = "业务指标一次发布形成的不可变定义和引用快照。")
public record MetricVersionResponse(
        @Schema(description = "指标发布版本记录 UUID。")
        UUID id,
        @Schema(description = "所属业务指标 UUID。")
        UUID metricId,
        @Schema(description = "所属指标内从 1 开始递增的发布版本号。")
        int version,
        @Schema(description = "本发布版本冻结的完整指标口径、结果绑定和参考资源。")
        MetricDefinition definition,
        @Schema(description = "发布时冻结的模型、字段、指标和数据服务引用快照。")
        List<MetricReferenceSnapshot> references,
        @Schema(description = "该不可变版本的发布时间，ISO-8601 UTC 时间戳。")
        Instant publishedAt,
        @Schema(description = "发布操作用户。")
        String publishedBy,
        @Schema(description = "发布人填写的本版本变更说明。")
        String changeNote
) {}
