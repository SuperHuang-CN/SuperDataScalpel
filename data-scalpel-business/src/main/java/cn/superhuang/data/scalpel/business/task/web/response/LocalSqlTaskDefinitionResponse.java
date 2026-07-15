package cn.superhuang.data.scalpel.business.task.web.response;

import cn.superhuang.data.scalpel.business.task.domain.LocalSqlWriteMode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LocalSqlTaskDefinitionResponse(
        UUID taskId,
        boolean configured,
        int version,
        String sql,
        List<TaskModelReferenceResponse> inputs,
        TaskModelReferenceResponse output,
        TaskDataSourceReferenceResponse resolvedDataSource,
        LocalSqlWriteMode writeMode,
        Integer timeoutSeconds,
        Instant updatedAt
) {

    public static LocalSqlTaskDefinitionResponse unconfigured(UUID taskId) {
        return new LocalSqlTaskDefinitionResponse(
                taskId, false, 0, null, List.of(), null, null, LocalSqlWriteMode.APPEND, 300, null
        );
    }
}
