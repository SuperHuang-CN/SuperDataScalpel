package cn.superhuang.data.scalpel.business.metric.web.response;

import io.swagger.v3.oas.annotations.media.Schema;import cn.superhuang.data.scalpel.business.metric.domain.*;
import java.util.*;
import java.time.Instant;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
@Schema(description = "业务指标基础资料、默认展示定义、健康状态和资源引用快照。")
public record MetricResponse(
        @Schema(description = "业务指标 UUID。")
        UUID id,
        @Schema(description = "指标稳定技术编码，创建后不可修改。")
        String code,
        @Schema(description = "指标显示名称。")
        String name,
        @Schema(description = "指标类型：ATOMIC 原子、DERIVED 派生或 COMPOSITE 复合。")
        MetricKind kind,
        @Schema(description = "所属目录 UUID；位于根目录时为空。")
        UUID directoryId,
        @Schema(description = "指标业务负责人或责任部门名称。")
        String ownerName,
        @Schema(description = "指标业务含义、使用范围或口径摘要。")
        String summary,
        @Schema(description = "指标生命周期：DRAFT 草稿，PUBLISHED 已发布可稳定引用，DISABLED 已停用。")
        MetricStatus status,
        @Schema(description = "当前已发布版本号；尚未发布时为空。")
        Integer publishedVersion,
        @Schema(description = "当前草稿是否包含尚未发布的变更。")
        boolean hasDraftChanges,
        @Schema(description = "默认展示的有效指标定义：已发布时为当前发布快照，从未发布时为草稿。")
        MetricDefinition definition,
        @Schema(description = "当前定义和引用的健康检查结果。")
        MetricHealthResponse health,
        @Schema(description = "默认展示定义引用的模型、字段、指标和数据服务解析快照。")
        List<MetricReferenceSnapshot> references,
        @Schema(description = "创建时间，ISO-8601 UTC 时间戳。")
        Instant createdAt,
        @Schema(description = "最后更新时间，ISO-8601 UTC 时间戳。")
        Instant updatedAt
) {}
