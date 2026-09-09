package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record SpatialCenterDispersionConfiguration(
        String sourceTableName,
        String pointGeometryColumnName,
        String featureIdColumnName,
        List<String> groupByColumns,
        String weightColumnName,
        List<SpatialCenterDispersionAnalysis> analyses,
        String outputTableName,
        SpatialCenterResultMode resultMode
) {
    public static final int MAX_GROUP_COLUMNS = 8;
    public static final int MAX_ANALYSES = 16;

    public SpatialCenterDispersionConfiguration {
        groupByColumns = groupByColumns == null ? null : List.copyOf(groupByColumns);
        analyses = analyses == null ? null : List.copyOf(analyses);
    }
    public SpatialCenterDispersionConfiguration(String sourceTableName, String pointGeometryColumnName, String featureIdColumnName,
            List<String> groupByColumns, String weightColumnName, List<SpatialCenterDispersionAnalysis> analyses, String outputTableName) {
        this(sourceTableName, pointGeometryColumnName, featureIdColumnName, groupByColumns, weightColumnName, analyses, outputTableName, null);
    }
    public boolean separateResults() { return resultMode == SpatialCenterResultMode.ANALYSIS_TABLES; }
}
