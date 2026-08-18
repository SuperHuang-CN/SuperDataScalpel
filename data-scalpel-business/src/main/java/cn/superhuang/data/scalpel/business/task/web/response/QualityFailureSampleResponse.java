package cn.superhuang.data.scalpel.business.task.web.response;

import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record QualityFailureSampleResponse(
        UUID ruleId,
        String ruleName,
        long sampledRows,
        long violationRows,
        boolean truncated,
        boolean rowLocatable,
        List<Column> columns,
        List<Map<String, Object>> rows
) {
    public QualityFailureSampleResponse {
        columns = List.copyOf(columns);
        rows = List.copyOf(rows);
    }

    public record Column(
            UUID fieldId,
            String code,
            String name,
            PlatformTypeDefinition type,
            boolean primaryKey,
            boolean diagnostic
    ) {
    }
}
