package cn.superhuang.data.scalpel.dispatcher.management;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DispatcherRegistrationRequest(
        @NotNull UUID engineId,
        @NotNull @Valid DispatcherTopics topics,
        @NotNull @Valid DispatcherAdmissionPolicy admissionPolicy
) {
}
