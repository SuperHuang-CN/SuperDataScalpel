package cn.superhuang.data.scalpel.business.compute.client;

public record DispatcherAdmissionPolicy(
        int maxQueuedExecutions,
        int maxConcurrentSubmissions,
        int maxInFlightApplications
) {
}
