package cn.superhuang.data.scalpel.business.compute.client;

import java.util.UUID;

public record DispatcherRegistrationRequest(
        UUID engineId,
        DispatcherTopics topics,
        DispatcherAdmissionPolicy admissionPolicy
) {
}
