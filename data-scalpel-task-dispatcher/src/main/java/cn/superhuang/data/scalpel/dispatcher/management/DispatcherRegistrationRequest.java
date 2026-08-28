package cn.superhuang.data.scalpel.dispatcher.management;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;
import cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourcePolicy;

public record DispatcherRegistrationRequest(
        @NotNull UUID engineId,
        @NotNull @Valid DispatcherTopics topics,
        @NotNull @Valid DispatcherAdmissionPolicy admissionPolicy,
        @NotNull @Valid SparkExecutionResourcePolicy resourcePolicy
) {
}
