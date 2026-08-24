package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record DeriveColumnsConfiguration(
        List<ColumnDerivation> globalDerivations,
        List<DeriveColumnsOperation> operations
) {
    public DeriveColumnsConfiguration {
        globalDerivations = globalDerivations == null ? List.of() : List.copyOf(globalDerivations);
        operations = operations == null ? null : List.copyOf(operations);
    }

    public DeriveColumnsConfiguration(List<DeriveColumnsOperation> operations) {
        this(List.of(), operations);
    }

    public DeriveColumnsConfiguration(String sourceTableName, String outputTableName, List<ColumnDerivation> derivations) {
        this(List.of(), List.of(new DeriveColumnsOperation(ProcessorOperation.LEGACY_OPERATION_ID, sourceTableName,
                new ProcessorOutput.CreateNewTable(outputTableName), derivations)));
    }
    private DeriveColumnsOperation single() { return operations == null || operations.size() != 1 ? null : operations.getFirst(); }
    public String sourceTableName() { return single() == null ? null : single().sourceTableName(); }
    public String outputTableName() { return single() == null || single().output() == null ? null : single().output().outputTableName(); }
    public List<ColumnDerivation> derivations() { return single() == null ? null : single().derivations(); }
}
