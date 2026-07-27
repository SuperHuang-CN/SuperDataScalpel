package cn.superhuang.data.scalpel.dispatcher.management;

import jakarta.validation.constraints.Min;

public record DispatcherAdmissionPolicy(
        @Min(0) int maxQueuedExecutions,
        @Min(1) int maxConcurrentSubmissions,
        @Min(0) int maxInFlightApplications
) {
}
