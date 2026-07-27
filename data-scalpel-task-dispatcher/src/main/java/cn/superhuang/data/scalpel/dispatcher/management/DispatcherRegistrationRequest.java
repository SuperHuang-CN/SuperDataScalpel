package cn.superhuang.data.scalpel.dispatcher.management;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DispatcherRegistrationRequest(
        @Min(1) int protocolVersion,
        @NotNull UUID engineId,
        @Min(1) long configRevision,
        @NotNull @Valid DispatcherTopics topics,
        @NotNull @Valid DispatcherAdmissionPolicy admissionPolicy
) {
}
