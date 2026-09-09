package cn.superhuang.data.scalpel.contract.task;

public record GeometrySimplifyConfiguration(
        String sourceTableName,
        String geometryColumnName,
        String outputTableName,
        String outputColumnName,
        GeometrySimplifyAlgorithm algorithm,
        Double tolerance,
        SpatialDistanceUnit toleranceUnit,
        GeometryUnaryPolicy geometryPolicy
) {
    public GeometrySimplifyConfiguration(String sourceTableName, String geometryColumnName, String outputTableName,
            String outputColumnName, GeometrySimplifyAlgorithm algorithm, double tolerance, SpatialDistanceUnit toleranceUnit) {
        this(sourceTableName, geometryColumnName, outputTableName, outputColumnName, algorithm, tolerance, toleranceUnit, null);
    }
}
