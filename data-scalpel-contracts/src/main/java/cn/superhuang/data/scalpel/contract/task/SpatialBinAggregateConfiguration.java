package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record SpatialBinAggregateConfiguration(
        String sourceTableName,
        String pointGeometryColumnName,
        SpatialBinShape binShape,
        double binSize,
        SpatialDistanceUnit binSizeUnit,
        boolean includeEmptyBins,
        List<SpatialBinStatistic> statistics,
        SpatialGroupSummary groupSummary,
        SpatialTemporalSlicing temporalSlicing,
        String outputTableName,
        String binIdColumnName,
        String binGeometryColumnName,
        SpatialBinSizeSemantics binSizeSemantics,
        SpatialH3Options h3,
        SpatialPlanarGridOptions planarGrid
) {
    public static final int MAX_STATISTICS = 32;

    public SpatialBinAggregateConfiguration {
        statistics = statistics == null ? null : List.copyOf(statistics);
    }

    public SpatialBinSizeSemantics effectiveBinSizeSemantics() {
        return binSizeSemantics == null ? SpatialBinSizeSemantics.LEGACY_SIDE_LENGTH : binSizeSemantics;
    }

    public SpatialBinAggregateConfiguration(
            String sourceTableName, String pointGeometryColumnName, SpatialBinShape binShape, double binSize,
            SpatialDistanceUnit binSizeUnit, boolean includeEmptyBins, List<SpatialBinStatistic> statistics,
            SpatialGroupSummary groupSummary, SpatialTemporalSlicing temporalSlicing, String outputTableName,
            String binIdColumnName, String binGeometryColumnName, SpatialBinSizeSemantics binSizeSemantics, SpatialH3Options h3
    ) {
        this(sourceTableName, pointGeometryColumnName, binShape, binSize, binSizeUnit, includeEmptyBins,
                statistics, groupSummary, temporalSlicing, outputTableName, binIdColumnName, binGeometryColumnName,
                binSizeSemantics, h3, null);
    }

    public SpatialBinAggregateConfiguration(
            String sourceTableName, String pointGeometryColumnName, SpatialBinShape binShape, double binSize,
            SpatialDistanceUnit binSizeUnit, boolean includeEmptyBins, List<SpatialBinStatistic> statistics,
            SpatialGroupSummary groupSummary, SpatialTemporalSlicing temporalSlicing, String outputTableName,
            String binIdColumnName, String binGeometryColumnName, SpatialBinSizeSemantics binSizeSemantics
    ) {
        this(sourceTableName, pointGeometryColumnName, binShape, binSize, binSizeUnit, includeEmptyBins,
                statistics, groupSummary, temporalSlicing, outputTableName, binIdColumnName, binGeometryColumnName, binSizeSemantics, null);
    }

    public SpatialBinAggregateConfiguration(
            String sourceTableName, String pointGeometryColumnName, SpatialBinShape binShape, double binSize,
            SpatialDistanceUnit binSizeUnit, boolean includeEmptyBins, List<SpatialBinStatistic> statistics,
            SpatialGroupSummary groupSummary, SpatialTemporalSlicing temporalSlicing, String outputTableName,
            String binIdColumnName, String binGeometryColumnName
    ) {
        this(sourceTableName, pointGeometryColumnName, binShape, binSize, binSizeUnit, includeEmptyBins,
                statistics, groupSummary, temporalSlicing, outputTableName, binIdColumnName, binGeometryColumnName, null);
    }
}
