package cn.superhuang.data.scalpel.business.operations.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "运行工作台中的计算引擎总量及可用性指标。")
public record RuntimeEngineMetrics(
        @Schema(description = "纳入运维工作台的计算引擎总数。")
        long total,
        @Schema(description = "注册生命周期为 ACTIVE 的计算引擎数量；其中仍可能包含不可达、依赖未就绪或观测未知的引擎。")
        long active,
        @Schema(description = "ACTIVE 且最近有效观测为 UNREACHABLE 的引擎数量。")
        long unreachable,
        @Schema(description = "ACTIVE、最近有效观测可达且 dependenciesReady=false 的引擎数量。")
        long notReady,
        @Schema(description = "ACTIVE 但从未观测、观测已过期、观测状态 UNKNOWN，或可达但依赖就绪状态缺失的引擎数量。")
        long unknown
) {}
