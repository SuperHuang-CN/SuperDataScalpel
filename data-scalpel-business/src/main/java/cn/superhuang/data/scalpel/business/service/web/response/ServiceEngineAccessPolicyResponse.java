package cn.superhuang.data.scalpel.business.service.web.response;

import cn.superhuang.data.scalpel.business.service.domain.ServiceEngineAccessPolicyStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ServiceEngineAccessPolicyResponse(
        UUID engineId,
        List<String> allowCidrs,
        List<String> denyCidrs,
        long desiredRevision,
        long appliedRevision,
        ServiceEngineAccessPolicyStatus status,
        String lastError,
        Instant appliedAt,
        Instant updatedAt
) {
}
