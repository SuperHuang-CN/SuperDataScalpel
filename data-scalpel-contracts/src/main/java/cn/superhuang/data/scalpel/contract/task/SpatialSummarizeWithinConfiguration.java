package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record SpatialSummarizeWithinConfiguration(
        String areaTableName,
        String areaGeometryColumnName,
        String summaryTableName,
        String summaryGeometryColumnName,
        boolean includeEmptyAreas,
        SpatialDistanceMethod distanceMethod,
        SpatialDistanceUnit lengthUnit,
        SpatialAreaUnit areaUnit,
        List<JoinOutputColumn> areaOutputColumns,
        List<SpatialWithinStatistic> statistics,
        SpatialGroupSummary groupSummary,
        SpatialTemporalSlicing temporalSlicing,
        String outputTableName,
        SpatialWithinGroupResult groupResult,
        SpatialWithinRegions regions
) {
    public static final int MAX_STATISTICS = 32;

    public SpatialSummarizeWithinConfiguration(String areaTableName, String areaGeometryColumnName,
            String summaryTableName, String summaryGeometryColumnName, boolean includeEmptyAreas,
            SpatialDistanceMethod distanceMethod, SpatialDistanceUnit lengthUnit, SpatialAreaUnit areaUnit,
            List<JoinOutputColumn> areaOutputColumns, List<SpatialWithinStatistic> statistics,
            SpatialGroupSummary groupSummary, SpatialTemporalSlicing temporalSlicing, String outputTableName,
            SpatialWithinGroupResult groupResult) {
        this(areaTableName, areaGeometryColumnName, summaryTableName, summaryGeometryColumnName, includeEmptyAreas,
                distanceMethod, lengthUnit, areaUnit, areaOutputColumns, statistics, groupSummary, temporalSlicing,
                outputTableName, groupResult, null);
    }

    public boolean usesGridRegions() { return regions != null && regions.usesGrid(); }

    public SpatialSummarizeWithinConfiguration(String areaTableName, String areaGeometryColumnName,
            String summaryTableName, String summaryGeometryColumnName, boolean includeEmptyAreas,
            SpatialDistanceMethod distanceMethod, SpatialDistanceUnit lengthUnit, SpatialAreaUnit areaUnit,
            List<JoinOutputColumn> areaOutputColumns, List<SpatialWithinStatistic> statistics,
            SpatialGroupSummary groupSummary, SpatialTemporalSlicing temporalSlicing, String outputTableName) {
        this(areaTableName, areaGeometryColumnName, summaryTableName, summaryGeometryColumnName,
                includeEmptyAreas, distanceMethod, lengthUnit, areaUnit, areaOutputColumns, statistics,
                groupSummary, temporalSlicing, outputTableName, null);
    }

    public boolean usesLinkedGroupResult() {
        return groupSummary != null && groupResult != null && groupResult.mode() != SpatialWithinGroupResultMode.LEGACY_FLAT;
    }

    public SpatialSummarizeWithinConfiguration {
        areaOutputColumns = areaOutputColumns == null ? null : List.copyOf(areaOutputColumns);
        statistics = statistics == null ? null : List.copyOf(statistics);
    }

    public boolean usesExplicitStatistics() {
        return statistics != null && statistics.stream().filter(java.util.Objects::nonNull)
                .anyMatch(SpatialWithinStatistic::requiresExplicitStatisticsVersion);
    }
}
