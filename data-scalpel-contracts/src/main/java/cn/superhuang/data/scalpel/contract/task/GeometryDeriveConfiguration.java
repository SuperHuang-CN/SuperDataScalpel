package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record GeometryDeriveConfiguration(
        String sourceTableName,
        String outputTableName,
        List<GeometryDerivation> derivations
) {
    public static final int MAX_DERIVATIONS = 32;

    public GeometryDeriveConfiguration {
        derivations = derivations == null ? null : List.copyOf(derivations);
    }
}
