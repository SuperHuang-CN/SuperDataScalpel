package cn.superhuang.data.scalpel.dialect.query.lineage;

import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;

import java.util.List;

/** Database-neutral provenance extracted from a read-only SELECT AST. */
public record SqlLineageAnalysis(
        AnalysisStatus status,
        boolean tableReferencesReliable,
        List<TableReference> tableReferences,
        List<Output> outputs,
        List<FieldUsage> fieldUsages,
        List<Warning> warnings
) {
    public SqlLineageAnalysis {
        tableReferences = tableReferences == null ? List.of() : List.copyOf(tableReferences);
        outputs = outputs == null ? List.of() : List.copyOf(outputs);
        fieldUsages = fieldUsages == null ? List.of() : List.copyOf(fieldUsages);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public enum AnalysisStatus { COMPLETE, PARTIAL, UNAVAILABLE }

    public enum TableMatchStatus { MATCHED, UNDECLARED, AMBIGUOUS, SYSTEM }

    public enum OutputKind { DIRECT, CALCULATED, AGGREGATED, CONSTANT, NULL_FILLED, UNKNOWN }

    public enum FieldUsageKind { JOIN_KEY, FILTER_CONDITION, GROUP_KEY, SORT_KEY, PARTITION_KEY }

    public record FieldReference(String relationKey, String fieldKey) {
    }

    public record TableReference(
            String displayName,
            TableIdentifier table,
            TableMatchStatus matchStatus,
            String relationKey
    ) {
    }

    public record Output(
            int ordinal,
            String outputName,
            OutputKind kind,
            boolean reliable,
            String expressionFingerprint,
            List<FieldReference> sources
    ) {
        public Output {
            sources = sources == null ? List.of() : List.copyOf(sources);
        }
    }

    public record FieldUsage(
            FieldReference field,
            String nodeKey,
            FieldUsageKind kind
    ) {
    }

    public record Warning(String code, String message, Integer outputOrdinal) {
    }
}
