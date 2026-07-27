package cn.superhuang.data.scalpel.business.compute.client;

import java.util.UUID;

public record DispatcherRegistrationRequest(
        int protocolVersion,
        UUID engineId,
        long configRevision,
        DispatcherTopics topics,
        DispatcherAdmissionPolicy admissionPolicy
) {
}
