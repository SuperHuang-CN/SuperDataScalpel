package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.task.domain.LocalSqlWriteMode;

import java.util.List;
import java.util.UUID;

/** Credential-free local definition snapshot used by simulated scheduled runs. */
public record ScheduledTaskRunSnapshot(
        List<UUID> inputModelIds,
        UUID outputModelId,
        String sql,
        LocalSqlWriteMode writeMode,
        int timeoutSeconds
) {

    public ScheduledTaskRunSnapshot {
        inputModelIds = inputModelIds == null ? List.of() : List.copyOf(inputModelIds);
    }
}
