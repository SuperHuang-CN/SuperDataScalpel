package cn.superhuang.data.scalpel.dialect.model;

import java.util.List;
import java.util.Objects;

/** Complete, SQL-free dialect conclusion for changing one physical table from one definition to another. */
public record TableChangePlan(
        TableDefinition before,
        TableDefinition target,
        TableChangeStrategy strategy,
        TableChangeRisk risk,
        TableDdlAtomicity atomicity,
        List<TableChangeOperation> operations,
        List<TableChangeCheck> checks,
        List<TableChangeReason> reasons,
        List<TableChangeExecutionOption> executionOptions
) {
    public TableChangePlan(
            TableDefinition before,
            TableDefinition target,
            TableChangeStrategy strategy,
            TableChangeRisk risk,
            TableDdlAtomicity atomicity,
            List<TableChangeOperation> operations,
            List<TableChangeCheck> checks,
            List<TableChangeReason> reasons
    ) {
        this(before, target, strategy, risk, atomicity, operations, checks, reasons, List.of());
    }

    public TableChangePlan {
        if (before == null || target == null) {
            throw new IllegalArgumentException("Source and target table definitions are required");
        }
        if (!Objects.equals(before.table(), target.table())) {
            throw new IllegalArgumentException("Physical table location cannot change in one table change plan");
        }
        if (strategy == null || risk == null || atomicity == null) {
            throw new IllegalArgumentException("Change plan strategy, risk, and atomicity are required");
        }
        operations = operations == null ? List.of() : List.copyOf(operations);
        checks = checks == null ? List.of() : List.copyOf(checks);
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        executionOptions = executionOptions == null ? List.of() : List.copyOf(executionOptions);

        if (strategy == TableChangeStrategy.METADATA_ONLY) {
            if (!operations.isEmpty() || atomicity != TableDdlAtomicity.NOT_APPLICABLE || risk != TableChangeRisk.SAFE
                    || !executionOptions.isEmpty()) {
                throw new IllegalArgumentException("Metadata-only plan cannot contain physical operations, DDL atomicity, or physical risk");
            }
        } else {
            if (operations.isEmpty()) {
                throw new IllegalArgumentException("Physical table change plan requires at least one operation");
            }
            if (strategy != TableChangeStrategy.UNSUPPORTED && atomicity == TableDdlAtomicity.NOT_APPLICABLE) {
                throw new IllegalArgumentException("Physical table change plan requires DDL atomicity");
            }
        }
        for (TableChangeOperation operation : operations) {
            if (!strategy.isAtLeastAsRestrictiveAs(operation.strategy())) {
                throw new IllegalArgumentException("Plan strategy cannot understate an operation strategy");
            }
            if (!risk.isAtLeastAsSevereAs(operation.risk())) {
                throw new IllegalArgumentException("Plan risk cannot understate an operation risk");
            }
        }
        for (TableChangeExecutionOption option : executionOptions) {
            if (option.mode() == TableChangeExecutionMode.IN_PLACE && !strategy.allowsInPlaceExecution()) {
                throw new IllegalArgumentException("Plan strategy does not permit in-place execution");
            }
            if (option.mode() == TableChangeExecutionMode.REBUILD && !strategy.allowsRebuildExecution()) {
                throw new IllegalArgumentException("Plan strategy does not permit rebuild execution");
            }
        }
    }

    public TableStructureFingerprint beforeFingerprint() {
        return before.structureFingerprint();
    }

    public TableStructureFingerprint targetFingerprint() {
        return target.structureFingerprint();
    }

    public boolean allowsInPlaceExecution() {
        return executionOptions.stream().anyMatch(option -> option.mode() == TableChangeExecutionMode.IN_PLACE);
    }

    public boolean allowsRebuildExecution() {
        return executionOptions.stream().anyMatch(option -> option.mode() == TableChangeExecutionMode.REBUILD);
    }

    public TableChangeExecutionOption requireExecutionOption(TableChangeExecutionMode mode) {
        return executionOptions.stream()
                .filter(option -> option.mode() == mode)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Change plan does not provide execution mode: " + mode));
    }
}
