package cn.superhuang.data.scalpel.business.operations.web.response;
import cn.superhuang.data.scalpel.business.compute.domain.*;
import cn.superhuang.data.scalpel.business.operations.domain.EngineObservationState;
import cn.superhuang.data.scalpel.business.compute.web.response.ComputeEngineRuntimeOverviewResponse;
import java.time.Instant;
import java.util.UUID;
public record RuntimeEngineResponse(UUID id, String name, ComputeBackendType backendType, ComputeEngineRegistrationState registrationState,
                                    EngineObservationState observationState, Boolean dependenciesReady, boolean stale,
                                    Instant attemptedAt, Instant observedAt, Instant lastHealthyAt, String summary,
                                    ComputeEngineRuntimeOverviewResponse snapshot) {}
