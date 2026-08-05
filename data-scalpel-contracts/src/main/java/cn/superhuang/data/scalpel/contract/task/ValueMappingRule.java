package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record ValueMappingRule(
        String columnName,
        List<ValueMappingEntry> entries,
        ValueMappingUnmatchedStrategy unmatchedStrategy,
        CanvasLiteral unmatchedValue
) {
    public ValueMappingRule {
        entries = entries == null ? null : List.copyOf(entries);
    }
}
