package cn.superhuang.data.scalpel.contract.task;

public record GeometryDerivation(
        String derivationId,
        GeometryDeriveKind kind,
        String sourceColumnName,
        String outputColumnName,
        GeometryUnaryPolicy geometryPolicy
) {
    public GeometryDerivation(String derivationId, GeometryDeriveKind kind, String sourceColumnName, String outputColumnName) {
        this(derivationId, kind, sourceColumnName, outputColumnName, null);
    }
}
