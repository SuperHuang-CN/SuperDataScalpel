package cn.superhuang.data.scalpel.dispatcher.backend;

import java.time.Instant;

public record BackendStatus(
        BackendExecutionState state,
        Instant startedAt,
        Instant endedAt,
        String safeErrorCode,
        String safeErrorMessage,
        String trackingUrl
) {
    public BackendStatus {
        if (state == null) throw new IllegalArgumentException("Backend 状态不能为空");
    }

    public BackendStatus(
            BackendExecutionState state,
            Instant startedAt,
            Instant endedAt,
            String safeErrorCode,
            String safeErrorMessage
    ) {
        this(state, startedAt, endedAt, safeErrorCode, safeErrorMessage, null);
    }
}
