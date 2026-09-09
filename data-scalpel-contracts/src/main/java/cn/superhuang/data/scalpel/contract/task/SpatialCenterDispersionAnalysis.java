package cn.superhuang.data.scalpel.contract.task;

public record SpatialCenterDispersionAnalysis(
        String analysisId,
        SpatialCenterDispersionKind kind,
        String outputColumnName,
        Integer standardDeviations,
        String outputTableName,
        java.util.List<SpatialCenterFeatureColumn> centralFeatureColumns
) {
    public SpatialCenterDispersionAnalysis {
        if (centralFeatureColumns != null) centralFeatureColumns = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(centralFeatureColumns));
    }
    public SpatialCenterDispersionAnalysis(String analysisId, SpatialCenterDispersionKind kind, String outputColumnName, Integer standardDeviations, String outputTableName) {
        this(analysisId, kind, outputColumnName, standardDeviations, outputTableName, null);
    }
    public SpatialCenterDispersionAnalysis(String analysisId, SpatialCenterDispersionKind kind, String outputColumnName, Integer standardDeviations) {
        this(analysisId, kind, outputColumnName, standardDeviations, null);
    }
}
