package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record SpatialNearestConfiguration(
        String sourceTableName,
        String sourceGeometryColumnName,
        String candidateTableName,
        String candidateGeometryColumnName,
        String candidateIdColumnName,
        SpatialDistanceMethod distanceMethod,
        int nearestCount,
        Double maximumDistance,
        SpatialDistanceUnit maximumDistanceUnit,
        boolean includeUnmatched,
        String outputTableName,
        String distanceColumnName,
        SpatialDistanceUnit distanceOutputUnit,
        String rankColumnName,
        List<JoinOutputColumn> outputColumns,
        SpatialNearestMatching matching
) {
    public static final int MAX_NEAREST_COUNT = 100;

    public SpatialNearestConfiguration {
        outputColumns = outputColumns == null ? null : List.copyOf(outputColumns);
    }

    public SpatialNearestConfiguration(String sourceTableName, String sourceGeometryColumnName, String candidateTableName,
            String candidateGeometryColumnName, String candidateIdColumnName, SpatialDistanceMethod distanceMethod,
            int nearestCount, Double maximumDistance, SpatialDistanceUnit maximumDistanceUnit, boolean includeUnmatched,
            String outputTableName, String distanceColumnName, SpatialDistanceUnit distanceOutputUnit, String rankColumnName,
            List<JoinOutputColumn> outputColumns) {
        this(sourceTableName, sourceGeometryColumnName, candidateTableName, candidateGeometryColumnName, candidateIdColumnName,
                distanceMethod, nearestCount, maximumDistance, maximumDistanceUnit, includeUnmatched, outputTableName,
                distanceColumnName, distanceOutputUnit, rankColumnName, outputColumns, null);
    }

    public boolean usesExactMatching() { return matching != null && matching.semantics() != SpatialNearestMatchSemantics.LEGACY_KNN; }
    public boolean outputsConnectionLines() { return usesExactMatching() && matching.connectionLines() != null && matching.connectionLines().active(); }
}
