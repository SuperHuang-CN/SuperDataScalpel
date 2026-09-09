package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record SpatialOverlayConfiguration(
        String leftTableName,
        String leftGeometryColumnName,
        String rightTableName,
        String rightGeometryColumnName,
        SpatialOverlayOperation operation,
        String outputTableName,
        String outputGeometryColumnName,
        List<JoinOutputColumn> outputColumns,
        SpatialOverlayGeometryPolicy geometryPolicy
) {
    public SpatialOverlayConfiguration {
        outputColumns = outputColumns == null ? null : List.copyOf(outputColumns);
    }

    public SpatialOverlayConfiguration(String leftTableName, String leftGeometryColumnName,
            String rightTableName, String rightGeometryColumnName, SpatialOverlayOperation operation,
            String outputTableName, String outputGeometryColumnName, List<JoinOutputColumn> outputColumns) {
        this(leftTableName, leftGeometryColumnName, rightTableName, rightGeometryColumnName,
                operation, outputTableName, outputGeometryColumnName, outputColumns, null);
    }

    public boolean usesFamilyGeometry() {
        return geometryPolicy == SpatialOverlayGeometryPolicy.FAMILY_2D
                || operation == SpatialOverlayOperation.IDENTITY
                || operation == SpatialOverlayOperation.SYMMETRICAL_DIFFERENCE;
    }

    public boolean requiresFamilyGeometryVersion() {
        return geometryPolicy != null || usesFamilyGeometry();
    }
}
