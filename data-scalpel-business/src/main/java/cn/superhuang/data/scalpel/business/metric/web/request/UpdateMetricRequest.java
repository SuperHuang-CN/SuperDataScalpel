package cn.superhuang.data.scalpel.business.metric.web.request;

import io.swagger.v3.oas.annotations.media.Schema;import cn.superhuang.data.scalpel.business.metric.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
@Schema(description = "修改业务指标基础资料；不改变草稿口径或已发布版本。")
public record UpdateMetricRequest(
        @Schema(description = "指标显示名称。")
        @NotBlank @Size(max=100) String name,
        @Schema(description = "指标类型：ATOMIC、DERIVED 或 COMPOSITE；指标首次发布后必须保持原类型。")
        @NotNull MetricKind kind,
        @Schema(description = "所属目录 UUID；位于根目录时为空。")
        UUID directoryId,
        @Schema(description = "指标业务负责人或责任部门名称。")
        @Size(max=100) String ownerName,
        @Schema(description = "指标业务含义、使用范围或口径摘要。")
        @Size(max=1000) String summary
) {}
