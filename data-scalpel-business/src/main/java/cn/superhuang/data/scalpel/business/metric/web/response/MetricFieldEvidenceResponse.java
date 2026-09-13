package cn.superhuang.data.scalpel.business.metric.web.response;

import io.swagger.v3.oas.annotations.media.Schema;import cn.superhuang.data.scalpel.business.metric.domain.*;
import java.util.*;
import java.time.Instant;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
@Schema(description = "某个任务定义版本中能够证明指标字段来源的血缘证据摘要。")
public record MetricFieldEvidenceResponse(
        @Schema(description = "生成这条血缘快照时使用的任务定义版本；可能是当前版本，也可能是仍被保留的历史版本。")
        Integer definitionVersion,
        @Schema(description = "证据是否来自任务当前保存定义；false 表示仅有历史版本证据。")
        boolean currentDefinition,
        @Schema(description = "该证据覆盖的结果模型字段 UUID 列表。")
        List<UUID> fieldIds,
        @Schema(description = "字段关联证据来源，例如当前任务定义血缘或历史运行血缘。")
        String source
) {}
