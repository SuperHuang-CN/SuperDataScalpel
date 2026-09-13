package cn.superhuang.data.scalpel.business.metric.web.request;

import io.swagger.v3.oas.annotations.media.Schema;import cn.superhuang.data.scalpel.business.metric.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
@Schema(description = "创建业务指标基础资料；计算口径通过独立草稿接口保存，创建不会发布指标。")
public record CreateMetricRequest(
        @Schema(description = "指标稳定技术编码，创建后不可修改。")
        @NotBlank @Pattern(regexp="[a-z][a-z0-9_]{0,63}") String code,
        @Schema(description = "指标显示名称。")
        @NotBlank @Size(max=100) String name,
        @Schema(description = "指标类型：ATOMIC 原子指标、DERIVED 派生指标、COMPOSITE 复合指标；首次发布后不能修改。")
        @NotNull MetricKind kind,
        @Schema(description = "所属目录 UUID；位于根目录时为空。")
        UUID directoryId,
        @Schema(description = "指标业务负责人或责任部门名称。")
        @Size(max=100) String ownerName,
        @Schema(description = "指标业务含义、使用范围或口径摘要。")
        @Size(max=1000) String summary
) {}
