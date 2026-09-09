package cn.superhuang.data.scalpel.contract.task;

public record SpatialPointClusterConfiguration(
        String sourceTableName,
        String pointGeometryColumnName,
        String featureIdColumnName,
        SpatialDistanceMethod distanceMethod,
        SpatialPointClusterParameters parameters,
        String outputTableName,
        String clusterIdColumnName,
        String noiseColumnName,
        SpatialDbscanOptions dbscan,
        SpatialHdbscanOptions hdbscan
) {
    public SpatialPointClusterConfiguration(String sourceTableName, String pointGeometryColumnName, String featureIdColumnName,
            SpatialDistanceMethod distanceMethod, SpatialPointClusterParameters parameters, String outputTableName,
            String clusterIdColumnName, String noiseColumnName, SpatialDbscanOptions dbscan) {
        this(sourceTableName, pointGeometryColumnName, featureIdColumnName, distanceMethod, parameters, outputTableName,
                clusterIdColumnName, noiseColumnName, dbscan, null);
    }
    public SpatialPointClusterConfiguration(String sourceTableName, String pointGeometryColumnName, String featureIdColumnName,
            SpatialDistanceMethod distanceMethod, SpatialPointClusterParameters parameters, String outputTableName,
            String clusterIdColumnName, String noiseColumnName) {
        this(sourceTableName, pointGeometryColumnName, featureIdColumnName, distanceMethod, parameters, outputTableName,
                clusterIdColumnName, noiseColumnName, null);
    }
}
