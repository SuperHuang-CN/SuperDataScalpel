package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record DeriveColumnsConfiguration(
        String sourceTableName,
        String outputTableName,
        List<ColumnDerivation> derivations
) {
    public DeriveColumnsConfiguration {
        derivations = derivations == null ? null : List.copyOf(derivations);
    }
}
