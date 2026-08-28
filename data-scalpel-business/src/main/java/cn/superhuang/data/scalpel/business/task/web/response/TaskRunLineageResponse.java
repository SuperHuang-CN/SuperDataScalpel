package cn.superhuang.data.scalpel.business.task.web.response;

import cn.superhuang.data.scalpel.business.lineage.domain.LineageCoverage;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TaskRunLineageResponse(
        UUID runId,
        String status,
        LineageCoverage coverage,
        boolean publishedSnapshot,
        Integer flowCount,
        Integer warningCount,
        List<Warning> warnings,
        String errorCode,
        String errorDetail,
        Instant completedAt
) {
    public TaskRunLineageResponse {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public record Warning(String code, String message, String flowKey) {
    }
}
