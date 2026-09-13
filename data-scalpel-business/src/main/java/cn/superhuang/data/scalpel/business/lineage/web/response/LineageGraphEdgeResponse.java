package cn.superhuang.data.scalpel.business.lineage.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.lineage.domain.LineageFieldDerivationType;
import cn.superhuang.data.scalpel.business.lineage.domain.LineageFieldUsageType;
import cn.superhuang.data.scalpel.business.lineage.domain.LineageOutputFieldEffect;

import java.util.List;

@Schema(description = "血缘图中一条有方向的读取、写入、派生、字段影响或服务暴露关系。")

public record LineageGraphEdgeResponse(
        @Schema(description = "血缘图内的边稳定键；只用于标识本条关系，不保证是数据库 UUID。")
        String id,
        @Schema(description = "关系起点节点的 id，必须对应同一图中 nodes 的某个节点。")
        String source,
        @Schema(description = "关系终点节点的 id，必须对应同一图中 nodes 的某个节点。")
        String target,
        @Schema(description = "关系类型：READS 表示任务读取资产，WRITES 表示任务写入资产，DERIVES 表示字段派生，FIELD_EFFECT 表示无明确来源的字段写入影响，EXPOSES 表示服务暴露模型或字段。")
        LineageGraphEdgeType type,
        @Schema(description = "字段派生方式；仅字段派生关系适用。DIRECT 为直接传递，CALCULATED 为表达式计算，AGGREGATED 为聚合产生。")
        LineageFieldDerivationType derivationType,
        @Schema(description = "任务对目标字段的写入结果；用于 FIELD_EFFECT 或写入关系，区分派生、常量、默认值、补空、保留和未写入等情况。")
        LineageOutputFieldEffect outputEffect,
        @Schema(description = "来源字段在任务中的非输出用途，例如连接键、过滤条件、分组键、排序键或分区键；没有此类用途时为空列表。")
        List<LineageFieldUsageType> usages,
        @Schema(description = "本条边关联的焦点字段稳定键；用于一次查询包含多个焦点字段时区分关系，没有关联字段时为空列表。")
        List<String> focusFieldKeys
) {
    public LineageGraphEdgeResponse {
        usages = usages == null ? List.of() : List.copyOf(usages);
        focusFieldKeys = focusFieldKeys == null ? List.of() : List.copyOf(focusFieldKeys);
    }

    public LineageGraphEdgeResponse(
            String id, String source, String target, LineageGraphEdgeType type,
            LineageFieldDerivationType derivationType, LineageOutputFieldEffect outputEffect,
            List<LineageFieldUsageType> usages
    ) {
        this(id, source, target, type, derivationType, outputEffect, usages, List.of());
    }
}
