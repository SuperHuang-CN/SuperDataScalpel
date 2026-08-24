package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record DeriveColumnsOperation(
        String operationId,
        String sourceTableName,
        ProcessorOutput output,
        List<ColumnDerivation> derivations
) implements ProcessorOperation {
    public DeriveColumnsOperation {
        derivations = derivations == null ? null : List.copyOf(derivations);
    }
}
