package cn.superhuang.data.scalpel.business.task.web.response;

import cn.superhuang.data.scalpel.contract.execution.UserJobMetricSnapshot;
import cn.superhuang.data.scalpel.contract.execution.UserJobStatus;

import java.util.List;

public record UserJobObservabilityResponse(
        UserJobStatus status,
        List<UserJobMetricSnapshot> metrics
) {
    public UserJobObservabilityResponse {
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
    }
}
