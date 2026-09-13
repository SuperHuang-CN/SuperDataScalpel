package cn.superhuang.data.scalpel.business.metric.web.response;

import io.swagger.v3.oas.annotations.media.Schema;import cn.superhuang.data.scalpel.business.metric.domain.*;
import java.util.*;
import java.time.Instant;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
@Schema(description = "指标定义和资源引用的管理元数据校验结果。草稿接口中用于判断草稿能否发布；指标详情和健康接口中描述默认展示定义的当前健康状态。不会连接物理数据源或验证实际指标数据。")
public record MetricHealthResponse(
        @Schema(description = "所检查定义是否满足当前发布校验；对于已发布版本，这是按当前元数据重新评估的结果，不改变既有发布状态。")
        boolean canPublish,
        @Schema(description = "结果绑定综合状态：UNBOUND 未提供 binding，VALID 已提供且未发现阻断性绑定问题，INVALID 存在资源缺失、字段类型错误或已发布结构变化等阻断问题。定义其他位置的问题可以使 canPublish=false 而 bindingStatus 仍为 VALID 或 UNBOUND。")
        String bindingStatus,
        @Schema(description = "当前指标定义、引用快照和发布依赖的健康问题；健康时为空列表。")
        List<MetricIssueResponse> issues
) {}
