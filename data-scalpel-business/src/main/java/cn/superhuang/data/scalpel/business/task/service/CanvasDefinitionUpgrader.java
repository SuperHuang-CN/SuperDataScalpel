package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.contract.task.CanvasDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasSpatialUnitVersions;
import cn.superhuang.data.scalpel.contract.task.ColumnTypeCast;
import cn.superhuang.data.scalpel.contract.task.GeometryDeriveNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.GeometrySimplifyNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.KafkaInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.KafkaInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.KafkaInputValueFormat;
import cn.superhuang.data.scalpel.contract.task.KafkaOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialAggregateNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialBinAggregateNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialBinShape;
import cn.superhuang.data.scalpel.contract.task.SpatialBinStatisticKind;
import cn.superhuang.data.scalpel.contract.task.SpatialCenterDispersionNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialClipNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialJoinNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialNearestGeodesicGeometryMode;
import cn.superhuang.data.scalpel.contract.task.SpatialNearestNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialOverlayNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialPointClusterNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialSummarizeWithinNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialTemporalSlicing;
import cn.superhuang.data.scalpel.contract.task.TdEngineTmqInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.TdEngineTmqInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.TrackBoundaryConfiguration;
import cn.superhuang.data.scalpel.contract.task.TrackDetectIncidentsNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.TrackFindDwellNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.TrackIncidentWindow;
import cn.superhuang.data.scalpel.contract.task.TrackMotionStatisticsNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.TrackReconstructNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.TrackSummaryStatistic;
import cn.superhuang.data.scalpel.contract.task.TrackSummaryStatisticKind;
import cn.superhuang.data.scalpel.contract.task.TypeCastNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.TypeCastOperation;
import cn.superhuang.data.scalpel.contract.task.UnionNodeDefinition;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Validates Canvas protocol compatibility and normalizes supported definitions to the current writer version. */
@Component
public class CanvasDefinitionUpgrader {

    public boolean supports(int schemaVersion, int schemaMinorVersion) {
        return schemaVersion == CanvasDefinition.CURRENT_SCHEMA_VERSION
                && schemaMinorVersion >= CanvasDefinition.LEGACY_SCHEMA_MINOR_VERSION
                && schemaMinorVersion <= CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION;
    }

    public void requireSupportedSource(CanvasDefinition definition) {
        if (definition.schemaVersion() == null
                || definition.schemaVersion() != CanvasDefinition.CURRENT_SCHEMA_VERSION) {
            invalid("Canvas schemaVersion 仅支持 " + CanvasDefinition.CURRENT_SCHEMA_VERSION);
        }
        int schemaMinorVersion = definition.effectiveSchemaMinorVersion();
        if (!supports(definition.schemaVersion(), schemaMinorVersion)) {
            invalid("Canvas schemaMinorVersion 仅支持 "
                    + CanvasDefinition.LEGACY_SCHEMA_MINOR_VERSION + " 到 "
                    + CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION);
        }
        if (definition.nodes() != null) {
            definition.nodes().stream()
                    .filter(java.util.Objects::nonNull)
                    .filter(node -> node.nodeType().introducedInMinorVersion() > schemaMinorVersion)
                    .findFirst()
                    .ifPresent(node -> invalid(node.nodeType() + " 从 Canvas "
                            + CanvasDefinition.CURRENT_SCHEMA_VERSION + "."
                            + node.nodeType().introducedInMinorVersion() + " 开始支持"));
        }
        requireMinorVersionFeatures(definition, schemaMinorVersion);
    }

    public CanvasDefinition upgradeToCurrent(CanvasDefinition definition) {
        return upgradeToCurrent(definition, KafkaInputConfiguration.DEFAULT_TRIGGER_INTERVAL_SECONDS);
    }

    public CanvasDefinition upgradeToCurrent(
            CanvasDefinition definition,
            int legacyTriggerIntervalSeconds
    ) {
        requireSupportedSource(definition);
        int normalizedLegacyInterval = legacyTriggerIntervalSeconds < 1 || legacyTriggerIntervalSeconds > 300
                ? KafkaInputConfiguration.DEFAULT_TRIGGER_INTERVAL_SECONDS
                : legacyTriggerIntervalSeconds;
        java.util.List<CanvasNodeDefinition> nodes = definition.nodes().stream()
                .map(node -> normalizeNode(node, normalizedLegacyInterval))
                .toList();
        if (definition.schemaVersion() == CanvasDefinition.CURRENT_SCHEMA_VERSION
                && definition.effectiveSchemaMinorVersion() == CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION
                && nodes.equals(definition.nodes())) {
            return definition;
        }
        return new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                nodes,
                definition.edges()
        );
    }

    private static CanvasNodeDefinition normalizeNode(
            CanvasNodeDefinition node,
            int legacyTriggerIntervalSeconds
    ) {
        if (node instanceof KafkaInputNodeDefinition input && input.configuration() != null) {
            KafkaInputConfiguration configuration = input.configuration();
            Integer triggerIntervalSeconds = configuration.triggerIntervalSeconds() == null
                    ? legacyTriggerIntervalSeconds : configuration.triggerIntervalSeconds();
            KafkaInputValueFormat valueFormat = configuration.effectiveValueFormat();
            java.util.List<cn.superhuang.data.scalpel.contract.task.KafkaInputMetadataField> metadataFields =
                    configuration.effectiveMetadataFields();
            if (configuration.triggerIntervalSeconds() != null
                    && configuration.valueFormat() != null
                    && configuration.metadataFields() != null) {
                return node;
            }
            return new KafkaInputNodeDefinition(input.id(), input.name(), input.layout(),
                    new KafkaInputConfiguration(
                            configuration.dataSourceId(), configuration.topic(), configuration.valueSchema(),
                            configuration.outputTableName(), configuration.startingOffsets(),
                            triggerIntervalSeconds, valueFormat, metadataFields));
        }
        if (node instanceof TdEngineTmqInputNodeDefinition input
                && input.configuration() != null
                && input.configuration().triggerIntervalSeconds() == null) {
            TdEngineTmqInputConfiguration configuration = input.configuration();
            return new TdEngineTmqInputNodeDefinition(input.id(), input.name(), input.layout(),
                    new TdEngineTmqInputConfiguration(
                            configuration.dataSourceId(), configuration.topicName(),
                            configuration.catalogName(), configuration.supertableName(),
                            configuration.topicDefinitionFingerprint(), configuration.outputTableName(),
                            configuration.startingOffsets(), configuration.maxOffsetsPerVGroupPerTrigger(),
                            legacyTriggerIntervalSeconds, configuration.eventTimeColumn(),
                            configuration.watermarkDelaySeconds()));
        }
        return node;
    }

    private static void requireMinorVersionFeatures(CanvasDefinition definition, int schemaMinorVersion) {
        if (definition.nodes() == null) return;
        for (int nodeIndex = 0; nodeIndex < definition.nodes().size(); nodeIndex++) {
            CanvasNodeDefinition node = definition.nodes().get(nodeIndex);
            requireCoreSpatialMinorVersionFeatures(node, nodeIndex, schemaMinorVersion);
            if (node instanceof TdEngineTmqInputNodeDefinition input
                    && input.configuration() != null
                    && schemaMinorVersion < 5
                    && (input.configuration().eventTimeColumn() != null
                    || input.configuration().watermarkDelaySeconds() != null)) {
                invalid("nodes[" + nodeIndex
                        + "].configuration.eventTimeColumn/watermarkDelaySeconds 从 Canvas "
                        + CanvasDefinition.CURRENT_SCHEMA_VERSION + ".5 开始支持");
            }
            if (node instanceof KafkaInputNodeDefinition input
                    && input.configuration() != null
                    && schemaMinorVersion < 4
                    && (input.configuration().valueFormat() != null
                    || input.configuration().metadataFields() != null)) {
                invalid("nodes[" + nodeIndex + "].configuration.valueFormat/metadataFields 从 Canvas "
                        + CanvasDefinition.CURRENT_SCHEMA_VERSION + ".4 开始支持");
            }
            if (node instanceof KafkaOutputNodeDefinition output
                    && output.configuration() != null
                    && schemaMinorVersion < 6) {
                for (int writeIndex = 0;
                     writeIndex < output.configuration().writes().size();
                     writeIndex++) {
                    var write = output.configuration().writes().get(writeIndex);
                    if (write != null && (write.valueFormat() != null
                            || write.valueColumnNames() != null && !write.valueColumnNames().isEmpty())) {
                        invalid("nodes[" + nodeIndex + "].configuration.writes[" + writeIndex
                                + "].valueFormat/valueColumnNames 从 Canvas "
                                + CanvasDefinition.CURRENT_SCHEMA_VERSION + ".6 开始支持");
                    }
                }
            }
            if (node instanceof SpatialJoinNodeDefinition join
                    && join.configuration() != null
                    && schemaMinorVersion < 60
                    && (join.configuration().spatialNear() != null
                    || join.configuration().distanceOutput() != null)) {
                invalid("nodes[" + nodeIndex
                        + "].configuration.spatialNear/distanceOutput 从 Canvas "
                        + CanvasDefinition.CURRENT_SCHEMA_VERSION + ".60 开始支持");
            }
            if (node instanceof SpatialAggregateNodeDefinition aggregate
                    && aggregate.configuration() != null
                    && aggregate.configuration().dissolve() != null
                    && schemaMinorVersion < 61
                    && aggregate.configuration().dissolve().groupingMode() != null) {
                invalid("nodes[" + nodeIndex
                        + "].configuration.dissolve.groupingMode 从 Canvas "
                        + CanvasDefinition.CURRENT_SCHEMA_VERSION + ".61 开始支持");
            }
            if (node instanceof UnionNodeDefinition union
                    && union.configuration() != null
                    && union.configuration().mergingTables() != null
                    && schemaMinorVersion < 62) {
                invalid("nodes[" + nodeIndex
                        + "].configuration.mergingTables 从 Canvas "
                        + CanvasDefinition.CURRENT_SCHEMA_VERSION + ".62 开始支持");
            }
            if (node instanceof SpatialClipNodeDefinition clip
                    && clip.configuration() != null
                    && clip.configuration().maskCombination() != null
                    && schemaMinorVersion < 77) {
                invalid("SPATIAL_CLIP_MASK_COMBINATION_REQUIRE_SCHEMA_VERSION：nodes[" + nodeIndex
                        + "].configuration.maskCombination 从 Canvas "
                        + CanvasDefinition.CURRENT_SCHEMA_VERSION + ".77 开始支持");
            }
            if (!(node instanceof TypeCastNodeDefinition typeCast)
                    || typeCast.configuration() == null
                    || typeCast.configuration().operations() == null) continue;
            for (int operationIndex = 0;
                 operationIndex < typeCast.configuration().operations().size();
                 operationIndex++) {
                TypeCastOperation operation = typeCast.configuration().operations().get(operationIndex);
                if (operation == null || operation.casts() == null) continue;
                for (int castIndex = 0; castIndex < operation.casts().size(); castIndex++) {
                    ColumnTypeCast cast = operation.casts().get(castIndex);
                    if (cast != null && cast.epochTimestampUnit() != null) {
                        if (schemaMinorVersion < 2) {
                            invalid("nodes[" + nodeIndex + "].configuration.operations[" + operationIndex
                                    + "].casts[" + castIndex + "].epochTimestampUnit 从 Canvas "
                                    + CanvasDefinition.CURRENT_SCHEMA_VERSION + ".2 开始支持");
                        }
                        if (schemaMinorVersion < 8
                                && cast.targetType() != null
                                && cast.targetType().type()
                                == cn.superhuang.data.scalpel.contract.type.PlatformDataType.LONG) {
                            invalid("nodes[" + nodeIndex + "].configuration.operations[" + operationIndex
                                    + "].casts[" + castIndex + "] 的 DATE/TIMESTAMP 转 LONG Epoch 单位从 Canvas "
                                    + CanvasDefinition.CURRENT_SCHEMA_VERSION + ".8 开始支持");
                        }
                    }
                    if (cast != null && schemaMinorVersion < 3 && cast.stringTemporalParseOptions() != null) {
                        invalid("nodes[" + nodeIndex + "].configuration.operations[" + operationIndex
                                + "].casts[" + castIndex + "].stringTemporalParseOptions 从 Canvas "
                                + CanvasDefinition.CURRENT_SCHEMA_VERSION + ".3 开始支持");
                    }
                    if (cast != null && schemaMinorVersion < 7 && cast.temporalStringFormatOptions() != null) {
                        invalid("nodes[" + nodeIndex + "].configuration.operations[" + operationIndex
                                + "].casts[" + castIndex + "].temporalStringFormatOptions 从 Canvas "
                                + CanvasDefinition.CURRENT_SCHEMA_VERSION + ".7 开始支持");
                    }
                }
            }
        }
    }

    private static void requireCoreSpatialMinorVersionFeatures(
            CanvasNodeDefinition node,
            int nodeIndex,
            int schemaMinorVersion
    ) {
        if (node == null) return;
        String path = "nodes[" + nodeIndex + "]";

        if (schemaMinorVersion < 41
                && node instanceof SpatialSummarizeWithinNodeDefinition within
                && within.configuration() != null
                && within.configuration().regions() != null) {
            invalid("SPATIAL_WITHIN_REGIONS_REQUIRE_SCHEMA_VERSION：" + path
                    + ".configuration.regions 从 Canvas 4.41 开始支持");
        }
        if (schemaMinorVersion < 40
                && node instanceof SpatialPointClusterNodeDefinition cluster
                && cluster.configuration() != null
                && cluster.configuration().dbscan() != null) {
            invalid("SPATIAL_DBSCAN_OPTIONS_REQUIRE_SCHEMA_VERSION：" + path
                    + ".configuration.dbscan 从 Canvas 4.40 开始支持");
        }

        SpatialTemporalSlicing slicing = switch (node) {
            case SpatialBinAggregateNodeDefinition bin when bin.configuration() != null ->
                    bin.configuration().temporalSlicing();
            case SpatialSummarizeWithinNodeDefinition within when within.configuration() != null ->
                    within.configuration().temporalSlicing();
            default -> null;
        };
        if (schemaMinorVersion < 39 && slicing != null && slicing.calendar() != null) {
            invalid("SPATIAL_CALENDAR_WINDOW_REQUIRE_SCHEMA_VERSION：" + path
                    + ".configuration.temporalSlicing.calendar 从 Canvas 4.39 开始支持");
        }
        if (schemaMinorVersion < 38
                && node instanceof SpatialBinAggregateNodeDefinition bin
                && bin.configuration() != null
                && bin.configuration().planarGrid() != null) {
            invalid("SPATIAL_PLANAR_GRID_REQUIRE_SCHEMA_VERSION：" + path
                    + ".configuration.planarGrid 从 Canvas 4.38 开始支持");
        }
        if (schemaMinorVersion < 37
                && node instanceof SpatialSummarizeWithinNodeDefinition within
                && within.configuration() != null
                && within.configuration().statistics() != null) {
            for (int statisticIndex = 0;
                 statisticIndex < within.configuration().statistics().size();
                 statisticIndex++) {
                var statistic = within.configuration().statistics().get(statisticIndex);
                if (statistic != null && statistic.requiresWeightedDispersionVersion()) {
                    invalid("SPATIAL_WITHIN_WEIGHTED_DISPERSION_REQUIRE_SCHEMA_VERSION：" + path
                            + ".configuration.statistics[" + statisticIndex
                            + "].weighting 从 Canvas 4.37 开始支持");
                }
            }
        }
        for (String unitPath : CanvasSpatialUnitVersions.unsupportedPaths(node, schemaMinorVersion)) {
            invalid("SPATIAL_EXTENDED_UNITS_REQUIRE_SCHEMA_VERSION：" + path + "." + unitPath
                    + " 从 Canvas 4.36 开始支持");
        }
        for (String unitPath : CanvasSpatialUnitVersions.unsupportedDurationPaths(node, schemaMinorVersion)) {
            invalid("SPATIAL_DURATION_WEEKS_REQUIRE_SCHEMA_VERSION：" + path + "." + unitPath
                    + " 从 Canvas 4.47 开始支持");
        }

        java.util.List<TrackSummaryStatistic> trackSummaries = switch (node) {
            case TrackReconstructNodeDefinition track when track.configuration() != null ->
                    track.configuration().summaryStatistics();
            case TrackFindDwellNodeDefinition dwell when dwell.configuration() != null ->
                    dwell.configuration().summaryStatistics();
            default -> java.util.List.of();
        };
        if (schemaMinorVersion < 35
                && trackSummaries != null
                && trackSummaries.stream().anyMatch(statistic -> statistic != null
                && (statistic.kind() == TrackSummaryStatisticKind.COUNT_FIELD
                || statistic.kind() == TrackSummaryStatisticKind.ANY))) {
            invalid("TRACK_FIELD_STATISTICS_REQUIRE_SCHEMA_VERSION：" + path
                    + ".configuration.summaryStatistics 从 Canvas 4.35 开始支持");
        }
        if (schemaMinorVersion < 34
                && node instanceof SpatialBinAggregateNodeDefinition bin
                && bin.configuration() != null
                && bin.configuration().statistics() != null
                && bin.configuration().statistics().stream().anyMatch(statistic -> statistic != null
                && (statistic.kind() == SpatialBinStatisticKind.COUNT_FIELD
                || statistic.kind() == SpatialBinStatisticKind.ANY))) {
            invalid("SPATIAL_BIN_FIELD_STATISTICS_REQUIRE_SCHEMA_VERSION：" + path
                    + ".configuration.statistics 从 Canvas 4.34 开始支持");
        }
        if (schemaMinorVersion < 33
                && node instanceof SpatialBinAggregateNodeDefinition bin
                && bin.configuration() != null
                && (bin.configuration().binShape() == SpatialBinShape.H3
                || bin.configuration().h3() != null)) {
            invalid("SPATIAL_H3_REQUIRE_SCHEMA_VERSION：" + path
                    + ".configuration 从 Canvas 4.33 开始支持");
        }
        if (schemaMinorVersion < 32
                && node instanceof SpatialCenterDispersionNodeDefinition center
                && center.configuration() != null
                && center.configuration().analyses() != null
                && center.configuration().analyses().stream().anyMatch(analysis ->
                analysis != null && analysis.centralFeatureColumns() != null)) {
            invalid("SPATIAL_CENTER_PROJECTION_REQUIRE_SCHEMA_VERSION：" + path
                    + ".configuration 从 Canvas 4.32 开始支持");
        }
        if (schemaMinorVersion < 31
                && node instanceof SpatialCenterDispersionNodeDefinition center
                && center.configuration() != null
                && (center.configuration().resultMode() != null
                || center.configuration().analyses() != null
                && center.configuration().analyses().stream().anyMatch(analysis ->
                analysis != null && analysis.outputTableName() != null))) {
            invalid("SPATIAL_CENTER_RESULTS_REQUIRE_SCHEMA_VERSION：" + path
                    + ".configuration 从 Canvas 4.31 开始支持");
        }
        if (schemaMinorVersion < 30
                && node instanceof SpatialNearestNodeDefinition nearest
                && nearest.configuration() != null
                && nearest.configuration().matching() != null) {
            invalid("SPATIAL_NEAREST_MATCHING_REQUIRE_SCHEMA_VERSION：" + path
                    + ".configuration.matching 从 Canvas 4.30 开始支持");
        }
        if (schemaMinorVersion < 48
                && node instanceof SpatialNearestNodeDefinition nearest
                && nearest.configuration() != null
                && nearest.configuration().matching() != null
                && nearest.configuration().matching().geodesicGeometryMode()
                == SpatialNearestGeodesicGeometryMode.GEOMETRY) {
            invalid("SPATIAL_NEAREST_GEODESIC_GEOMETRY_REQUIRE_SCHEMA_VERSION：" + path
                    + ".configuration.matching.geodesicGeometryMode 从 Canvas 4.48 开始支持");
        }

        boolean unaryPolicy = node instanceof GeometryDeriveNodeDefinition derive
                && derive.configuration() != null
                && derive.configuration().derivations() != null
                && derive.configuration().derivations().stream().anyMatch(item ->
                item != null && item.geometryPolicy() != null)
                || node instanceof GeometrySimplifyNodeDefinition simplify
                && simplify.configuration() != null
                && simplify.configuration().geometryPolicy() != null;
        if (schemaMinorVersion < 29 && unaryPolicy) {
            invalid("GEOMETRY_UNARY_POLICY_REQUIRE_SCHEMA_VERSION：" + path
                    + ".configuration 从 Canvas 4.29 开始支持");
        }
        if (schemaMinorVersion < 46
                && node instanceof TrackDetectIncidentsNodeDefinition incident
                && incident.configuration() != null
                && !incident.configuration().conditionWindows().isEmpty()) {
            invalid("TRACK_INCIDENT_WINDOWS_REQUIRE_SCHEMA_VERSION：" + path
                    + ".configuration.conditionWindows 从 Canvas 4.46 开始支持");
        }
        if (schemaMinorVersion < 63
                && usesIncidentWindowSource(node, TrackIncidentWindow.Source.TRACK_DISTANCE)) {
            invalid("TRACK_INCIDENT_DISTANCE_WINDOWS_REQUIRE_SCHEMA_VERSION：" + path
                    + ".configuration.conditionWindows 的轨迹距离来源从 Canvas 4.63 开始支持");
        }
        if (schemaMinorVersion < 64
                && usesIncidentWindowSource(node, TrackIncidentWindow.Source.TRACK_SPEED)) {
            invalid("TRACK_INCIDENT_SPEED_WINDOWS_REQUIRE_SCHEMA_VERSION：" + path
                    + ".configuration.conditionWindows 的轨迹速度来源从 Canvas 4.64 开始支持");
        }
        if (schemaMinorVersion < 65
                && usesIncidentWindowSource(node, TrackIncidentWindow.Source.TRACK_ACCELERATION)) {
            invalid("TRACK_INCIDENT_ACCELERATION_WINDOWS_REQUIRE_SCHEMA_VERSION：" + path
                    + ".configuration.conditionWindows 的轨迹加速度来源从 Canvas 4.65 开始支持");
        }
        if (schemaMinorVersion < 66
                && node instanceof TrackDetectIncidentsNodeDefinition incident
                && incident.configuration() != null
                && !incident.configuration().conditionScalars().isEmpty()) {
            invalid("TRACK_INCIDENT_SCALARS_REQUIRE_SCHEMA_VERSION：" + path
                    + ".configuration.conditionScalars 从 Canvas 4.66 开始支持");
        }
        if (schemaMinorVersion < 67
                && node instanceof TrackDetectIncidentsNodeDefinition incident
                && incident.configuration() != null
                && incident.configuration().conditionScalars().stream().anyMatch(scalar ->
                scalar != null && scalar.isPointCoordinate())) {
            invalid("TRACK_INCIDENT_POINT_COORDINATES_REQUIRE_SCHEMA_VERSION：" + path
                    + ".configuration.conditionScalars 的 Point 坐标来源从 Canvas 4.67 开始支持");
        }
        if (schemaMinorVersion < 45
                && node instanceof SpatialPointClusterNodeDefinition cluster
                && cluster.configuration() != null
                && cluster.configuration().hdbscan() != null) {
            invalid("SPATIAL_HDBSCAN_OPTIONS_REQUIRE_SCHEMA_VERSION：" + path
                    + ".configuration.hdbscan 从 Canvas 4.45 开始支持");
        }
        if (node instanceof TrackReconstructNodeDefinition reconstruct
                && reconstruct.configuration() != null
                && reconstruct.configuration().reconstruction() != null) {
            var options = reconstruct.configuration().reconstruction();
            if (schemaMinorVersion < 44
                    && options.areaGeometry() != null
                    && options.areaGeometry().geodesicBoundary() != null) {
                invalid("TRACK_GEODESIC_AREA_REQUIRE_SCHEMA_VERSION：" + path
                        + ".configuration.reconstruction.areaGeometry.geodesicBoundary 从 Canvas 4.44 开始支持");
            }
            if (schemaMinorVersion < 43
                    && options.areaGeometry() != null
                    && !options.areaGeometry().windowBindings().isEmpty()) {
                invalid("TRACK_BUFFER_WINDOWS_REQUIRE_SCHEMA_VERSION：" + path
                        + ".configuration.reconstruction.areaGeometry.windowBindings 从 Canvas 4.43 开始支持");
            }
            if (schemaMinorVersion < 42 && options.areaGeometry() != null) {
                invalid("TRACK_AREA_GEOMETRY_REQUIRE_SCHEMA_VERSION：" + path
                        + ".configuration.reconstruction.areaGeometry 从 Canvas 4.42 开始支持");
            }
            if (schemaMinorVersion < 28 && options.pathGeometry() != null) {
                invalid("TRACK_PATH_GEOMETRY_REQUIRE_SCHEMA_VERSION：" + path
                        + ".configuration.reconstruction.pathGeometry 从 Canvas 4.28 开始支持");
            }
            if (schemaMinorVersion < 27) {
                invalid("TRACK_RECONSTRUCT_OPTIONS_REQUIRE_SCHEMA_VERSION：" + path
                        + ".configuration.reconstruction 从 Canvas 4.27 开始支持");
            }
        }
        if (schemaMinorVersion < 26
                && node instanceof SpatialOverlayNodeDefinition overlay
                && overlay.configuration() != null
                && overlay.configuration().requiresFamilyGeometryVersion()) {
            invalid("SPATIAL_OVERLAY_FAMILY_REQUIRE_SCHEMA_VERSION：" + path
                    + ".configuration.geometryPolicy 从 Canvas 4.26 开始支持");
        }
        if (schemaMinorVersion < 25
                && node instanceof SpatialSummarizeWithinNodeDefinition within
                && within.configuration() != null
                && within.configuration().groupResult() != null) {
            invalid(path + ".configuration.groupResult 从 Canvas 4.25 开始支持");
        }
        if (schemaMinorVersion < 24
                && node instanceof SpatialSummarizeWithinNodeDefinition within
                && within.configuration() != null
                && within.configuration().usesExplicitStatistics()) {
            invalid(path + ".configuration.statistics 从 Canvas 4.24 开始支持");
        }
        if (schemaMinorVersion < 23
                && node instanceof TrackMotionStatisticsNodeDefinition motion
                && motion.configuration() != null
                && (motion.configuration().motionSemantics() != null
                || motion.configuration().windowOptions() != null)) {
            invalid(path + ".configuration.motionSemantics 从 Canvas 4.23 开始支持");
        }
        if (schemaMinorVersion < 22
                && node instanceof TrackFindDwellNodeDefinition dwell
                && dwell.configuration() != null
                && (dwell.configuration().dwellSemantics() != null
                || dwell.configuration().rangeOptions() != null)) {
            invalid(path + ".configuration.dwellSemantics 从 Canvas 4.22 开始支持");
        }
        if (schemaMinorVersion < 21
                && node instanceof SpatialBinAggregateNodeDefinition bin
                && bin.configuration() != null
                && bin.configuration().binSizeSemantics() != null) {
            invalid(path + ".configuration.binSizeSemantics 从 Canvas 4.21 开始支持");
        }

        TrackBoundaryConfiguration boundaries = switch (node) {
            case TrackReconstructNodeDefinition track when track.configuration() != null ->
                    track.configuration().boundaries();
            case TrackMotionStatisticsNodeDefinition track when track.configuration() != null ->
                    track.configuration().boundaries();
            case TrackFindDwellNodeDefinition track when track.configuration() != null ->
                    track.configuration().boundaries();
            case TrackDetectIncidentsNodeDefinition track when track.configuration() != null ->
                    track.configuration().boundaries();
            default -> null;
        };
        if (schemaMinorVersion < 21
                && boundaries != null
                && boundaries.fixedTimeBoundary() != null) {
            invalid(path + ".configuration.boundaries.fixedTimeBoundary 从 Canvas 4.21 开始支持");
        }
        if (schemaMinorVersion < 21
                && node instanceof TrackDetectIncidentsNodeDefinition incident
                && incident.configuration() != null
                && incident.configuration().usesLifecycleOptions()) {
            invalid(path + ".configuration 事件生命周期配置从 Canvas 4.21 开始支持");
        }
    }

    private static boolean usesIncidentWindowSource(
            CanvasNodeDefinition node,
            TrackIncidentWindow.Source source
    ) {
        return node instanceof TrackDetectIncidentsNodeDefinition incident
                && incident.configuration() != null
                && incident.configuration().conditionWindows().stream().anyMatch(window ->
                window != null && window.effectiveSource() == source);
    }

    private static void invalid(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
