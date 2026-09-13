package cn.superhuang.data.scalpel.business.operations.web.response;

import io.swagger.v3.oas.annotations.media.Schema;import cn.superhuang.data.scalpel.business.compute.domain.*;
import cn.superhuang.data.scalpel.business.operations.domain.EngineObservationState;
import cn.superhuang.data.scalpel.business.compute.web.response.ComputeEngineRuntimeOverviewResponse;
import java.time.Instant;
import java.util.UUID;
@Schema(description = "运维工作台中的计算引擎注册状态、最近观测和运行时快照。")
public record RuntimeEngineResponse(
        @Schema(description = "计算引擎 UUID。")
        UUID id,
        @Schema(description = "计算引擎显示名称。")
        String name,
        @Schema(description = "计算后端类型，决定注册、提交和运行状态协议。")
        ComputeBackendType backendType,
        @Schema(description = "Admin 对计算引擎的注册生命周期状态；只有 ACTIVE 引擎参与有效健康观测，状态本身不表示当前可达。")
        ComputeEngineRegistrationState registrationState,
        @Schema(description = "最近有效连通性观测：REACHABLE 可达、UNREACHABLE 不可达、UNKNOWN 无当前有效证据；依赖就绪情况由 dependenciesReady 单独表达。")
        EngineObservationState observationState,
        @Schema(description = "引擎报告的运行依赖是否全部就绪；未成功观测或快照过期时为空。")
        Boolean dependenciesReady,
        @Schema(description = "最近一次成功观测是否超过允许时效；true 时快照不能代表当前实时状态。")
        boolean stale,
        @Schema(description = "最近一次开始尝试探测引擎的时间，ISO-8601 UTC 时间戳；从未探测时为空，失败尝试也会更新。")
        Instant attemptedAt,
        @Schema(description = "最近一次形成可达或不可达有效判断的时间，ISO-8601 UTC 时间戳；从未形成有效判断时为空。")
        Instant observedAt,
        @Schema(description = "最近一次确认引擎可达且运行依赖就绪的时间，ISO-8601 UTC 时间戳；从未达到健康条件时为空。")
        Instant lastHealthyAt,
        @Schema(description = "最近观测状态或不可用原因的安全摘要。")
        String summary,
        @Schema(description = "最近一次计算引擎运行时概览；从未成功读取时为空。")
        ComputeEngineRuntimeOverviewResponse snapshot
) {}
