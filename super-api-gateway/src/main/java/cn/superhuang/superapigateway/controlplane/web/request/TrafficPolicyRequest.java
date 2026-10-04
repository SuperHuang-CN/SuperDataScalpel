package cn.superhuang.superapigateway.controlplane.web.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Zero disables a numeric limit. Limits apply to each gateway node, not to the cluster. */
public record TrafficPolicyRequest(
        @Min(0) @Max(1_000_000) int requestsPerSecond,
        @Min(0) @Max(1_000_000) int consumerRequestsPerSecond,
        @Min(0) @Max(100_000) int maxConcurrentRequests,
        @Min(0) @Max(1_073_741_824L) long maxRequestBytes,
        @Size(max = 64) List<@NotBlank @Size(max = 64) String> allowedCidrs,
        @Size(max = 64) List<@NotBlank @Size(max = 64) String> deniedCidrs
) {
    public TrafficPolicyRequest {
        allowedCidrs = allowedCidrs == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(allowedCidrs));
        deniedCidrs = deniedCidrs == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(deniedCidrs));
    }
    public static TrafficPolicyRequest unrestricted() {
        return new TrafficPolicyRequest(0, 0, 0, 0, List.of(), List.of());
    }
}
