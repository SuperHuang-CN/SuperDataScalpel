package cn.superhuang.datascalpel.taskengine.compiler.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasEdgeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout;
import cn.superhuang.data.scalpel.contract.task.CompilationSeverity;
import cn.superhuang.data.scalpel.contract.task.CastFailureStrategy;
import cn.superhuang.data.scalpel.contract.task.ColumnTypeCast;
import cn.superhuang.data.scalpel.contract.task.EpochTimestampUnit;
import cn.superhuang.data.scalpel.contract.task.ProcessorOutput;
import cn.superhuang.data.scalpel.contract.task.SpatialClipConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialClipNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialAggregateConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialAggregateNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputConfiguration;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputWrite;
import cn.superhuang.data.scalpel.contract.task.JdbcColumnMapping;
import cn.superhuang.data.scalpel.contract.task.JdbcWriteMode;
import cn.superhuang.data.scalpel.contract.task.JoinCondition;
import cn.superhuang.data.scalpel.contract.task.JoinConfiguration;
import cn.superhuang.data.scalpel.contract.task.JoinNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JoinOperator;
import cn.superhuang.data.scalpel.contract.task.JoinType;
import cn.superhuang.data.scalpel.contract.task.KafkaInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.KafkaInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.KafkaStartingOffsets;
import cn.superhuang.data.scalpel.contract.task.KafkaValueSchema;
import cn.superhuang.data.scalpel.contract.task.ModelInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.ModelInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ModelInputSelection;
import cn.superhuang.data.scalpel.contract.task.ModelOutputConfiguration;
import cn.superhuang.data.scalpel.contract.task.ModelOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ModelOutputWrite;
import cn.superhuang.data.scalpel.contract.task.RenameConfiguration;
import cn.superhuang.data.scalpel.contract.task.RenameNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.TypeCastConfiguration;
import cn.superhuang.data.scalpel.contract.task.TypeCastNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.TypeCastOperation;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanvasGraphPlanTest {
    @Test void fixedWeekUnitsRequire47ButCalendarWeeksRemainCompatible() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var node = mapper.readValue("""
                {"id":"11111111-1111-4111-8111-111111111111","type":"TRACK_MOTION_STATISTICS","name":"WEEKS",
                 "layout":{"x":0,"y":0,"width":320,"height":200},"configuration":{
                 "boundaries":{"maximumTimeGapUnit":"WEEKS","fixedTimeBoundary":{"unit":"WEEKS"}},
                 "windowOptions":{"durationUnit":"WEEKS","idleTimeThresholdUnit":"WEEKS"},
                 "metrics":[{"kind":"DURATION","outputUnit":"WEEKS"}]}}
                """, cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition.class);
        String code = "SPATIAL_DURATION_WEEKS_REQUIRE_SCHEMA_VERSION";
        var old = CanvasGraphPlan.create(new CanvasDefinition(4,46,List.of(node),List.of()));
        for (String path : List.of("boundaries.maximumTimeGapUnit", "windowOptions.durationUnit", "windowOptions.idleTimeThresholdUnit", "metrics[0].outputUnit"))
            assertTrue(hasNodeIssueAtPath(old,0,code,"configuration." + path));
        assertFalse(hasNodeIssueAtPath(old,0,code,"configuration.boundaries.fixedTimeBoundary.unit"));
        assertFalse(hasNodeIssue(CanvasGraphPlan.create(new CanvasDefinition(4,47,List.of(node),List.of())),0,code));
    }

    @Test
    void gatesIncidentConditionWindowsEvenInInactiveLegacyDrafts() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (String semantics : List.of("LEGACY", "CONDITION_LIFECYCLE")) {
            var c = mapper.readValue("{\"incidentSemantics\":\"" + semantics + "\",\"conditionWindows\":[{\"bindingName\":\"past\",\"sourceColumnName\":\"speed\",\"kind\":\"MEAN\",\"startOffset\":-5,\"endOffset\":0}]}",
                    cn.superhuang.data.scalpel.contract.task.TrackDetectIncidentsConfiguration.class);
            var node = new cn.superhuang.data.scalpel.contract.task.TrackDetectIncidentsNodeDefinition(
                    UUID.randomUUID().toString(), "事件", new CanvasNodeLayout(0d,0d,360d,216d), c);
            var old = CanvasGraphPlan.create(new CanvasDefinition(4,45,List.of(node),List.of()));
            assertTrue(hasNodeIssueAtPath(old,0,"TRACK_INCIDENT_WINDOWS_REQUIRE_SCHEMA_VERSION","configuration.conditionWindows"));
            var current = CanvasGraphPlan.create(new CanvasDefinition(4,46,List.of(node),List.of()));
            assertFalse(hasNodeIssue(current,0,"TRACK_INCIDENT_WINDOWS_REQUIRE_SCHEMA_VERSION"));
        }
    }

    @Test void gatesHdbscanOptionsAt45IncludingInactiveDrafts() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (String algorithm : List.of("DBSCAN", "HDBSCAN")) {
            for (String options : List.of("null", "{}", "{\"probabilityColumnName\":\"p\"}")) {
                var c = mapper.readValue("{\"parameters\":{\"algorithm\":\"" + algorithm + "\",\"minimumFeatures\":2},\"hdbscan\":" + options + "}",
                        cn.superhuang.data.scalpel.contract.task.SpatialPointClusterConfiguration.class);
                var node = new cn.superhuang.data.scalpel.contract.task.SpatialPointClusterNodeDefinition(UUID.randomUUID().toString(), "聚类",
                        new CanvasNodeLayout(0d,0d,352d,216d), c);
                assertEquals(!options.equals("null"), hasNodeIssue(CanvasGraphPlan.create(new CanvasDefinition(4,44,List.of(node),List.of())),0,"SPATIAL_HDBSCAN_OPTIONS_REQUIRE_SCHEMA_VERSION"));
                assertFalse(hasNodeIssue(CanvasGraphPlan.create(new CanvasDefinition(4,45,List.of(node),List.of())),0,"SPATIAL_HDBSCAN_OPTIONS_REQUIRE_SCHEMA_VERSION"));
            }
        }
    }

    @Test
    void gatesCenterProjectionAt32IncludingInactiveAndEmptySettings() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (String fields : List.of("null", "[]", "[{\"sourceColumnName\":\"name\",\"outputColumnName\":\"label\",\"included\":true}]")) {
            var config = mapper.readValue("{\"resultMode\":\"LEGACY_WIDE\",\"analyses\":[{\"kind\":\"MEAN_CENTER\",\"centralFeatureColumns\":" + fields + "}]}", cn.superhuang.data.scalpel.contract.task.SpatialCenterDispersionConfiguration.class);
            var node = new cn.superhuang.data.scalpel.contract.task.SpatialCenterDispersionNodeDefinition(UUID.randomUUID().toString(), "中心", new CanvasNodeLayout(0d, 0d, 368d, 224d), config);
            assertEquals(!fields.equals("null"), hasNodeIssue(CanvasGraphPlan.create(new CanvasDefinition(4, 31, List.of(node), List.of())), 0, "SPATIAL_CENTER_PROJECTION_REQUIRE_SCHEMA_VERSION"));
            assertFalse(hasNodeIssue(CanvasGraphPlan.create(new CanvasDefinition(4, 32, List.of(node), List.of())), 0, "SPATIAL_CENTER_PROJECTION_REQUIRE_SCHEMA_VERSION"));
        }
    }

    @Test void gatesH3At33IncludingInactiveSettings() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (String json : List.of("{}", "{\"h3\":null}", "{\"binShape\":\"H3\"}",
                "{\"binShape\":\"SQUARE\",\"h3\":{\"mode\":\"RESOLUTION\",\"resolution\":null}}")) {
            var c = mapper.readValue(json, cn.superhuang.data.scalpel.contract.task.SpatialBinAggregateConfiguration.class);
            var node = new cn.superhuang.data.scalpel.contract.task.SpatialBinAggregateNodeDefinition(UUID.randomUUID().toString(), "格网", new CanvasNodeLayout(0d, 0d, 360d, 224d), c);
            boolean enabled = c.h3() != null || c.binShape() == cn.superhuang.data.scalpel.contract.task.SpatialBinShape.H3;
            assertEquals(enabled, hasNodeIssue(CanvasGraphPlan.create(new CanvasDefinition(4, 32, List.of(node), List.of())), 0, "SPATIAL_H3_REQUIRE_SCHEMA_VERSION"));
            assertFalse(hasNodeIssue(CanvasGraphPlan.create(new CanvasDefinition(4, 33, List.of(node), List.of())), 0, "SPATIAL_H3_REQUIRE_SCHEMA_VERSION"));
        }
    }

    @Test void gatesBinFieldCountAndAnyAt34() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (String kind : List.of("COUNT", "COUNT_FIELD", "ANY")) {
            var c = mapper.readValue("{\"statistics\":[{\"kind\":\"" + kind + "\"}]}", cn.superhuang.data.scalpel.contract.task.SpatialBinAggregateConfiguration.class);
            var node = new cn.superhuang.data.scalpel.contract.task.SpatialBinAggregateNodeDefinition(UUID.randomUUID().toString(), "格网", new CanvasNodeLayout(0d, 0d, 360d, 224d), c);
            assertEquals(!kind.equals("COUNT"), hasNodeIssue(CanvasGraphPlan.create(new CanvasDefinition(4, 33, List.of(node), List.of())), 0, "SPATIAL_BIN_FIELD_STATISTICS_REQUIRE_SCHEMA_VERSION"));
            assertFalse(hasNodeIssue(CanvasGraphPlan.create(new CanvasDefinition(4, 34, List.of(node), List.of())), 0, "SPATIAL_BIN_FIELD_STATISTICS_REQUIRE_SCHEMA_VERSION"));
        }
    }

    @Test void gatesTrackFieldStatisticsAt35EvenWhenDwellSummariesAreInactive() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (String kind : List.of("COUNT", "COUNT_FIELD", "ANY")) {
            String json = "{\"summaryStatistics\":[{\"kind\":\"" + kind + "\"}]}";
            var track = mapper.readValue(json, cn.superhuang.data.scalpel.contract.task.TrackReconstructConfiguration.class);
            var dwell = mapper.readValue(json.substring(0, json.length() - 1) + ",\"dwellSemantics\":\"REFERENCE_CENTER\",\"rangeOptions\":{\"resultMode\":\"ALL_FEATURES\"}}",
                    cn.superhuang.data.scalpel.contract.task.TrackFindDwellConfiguration.class);
            var layout = new CanvasNodeLayout(0d, 0d, 360d, 224d);
            for (var node : List.<cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition>of(
                    new cn.superhuang.data.scalpel.contract.task.TrackReconstructNodeDefinition(UUID.randomUUID().toString(), "轨迹", layout, track),
                    new cn.superhuang.data.scalpel.contract.task.TrackFindDwellNodeDefinition(UUID.randomUUID().toString(), "驻留", layout, dwell))) {
                assertEquals(!kind.equals("COUNT"), hasNodeIssue(CanvasGraphPlan.create(new CanvasDefinition(4, 34, List.of(node), List.of())), 0, "TRACK_FIELD_STATISTICS_REQUIRE_SCHEMA_VERSION"));
                assertFalse(hasNodeIssue(CanvasGraphPlan.create(new CanvasDefinition(4, 35, List.of(node), List.of())), 0, "TRACK_FIELD_STATISTICS_REQUIRE_SCHEMA_VERSION"));
            }
        }
    }

    @Test
    void gatesCenterIndependentResultsAt31EvenWhenLegacyModeIsSelected() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (String json : List.of("{}", "{\"resultMode\":null}", "{\"resultMode\":\"ANALYSIS_TABLES\"}",
                "{\"resultMode\":\"LEGACY_WIDE\"}", "{\"analyses\":[{\"outputTableName\":\"mean\"}]}")) {
            var c = mapper.readValue(json, cn.superhuang.data.scalpel.contract.task.SpatialCenterDispersionConfiguration.class);
            var node = new cn.superhuang.data.scalpel.contract.task.SpatialCenterDispersionNodeDefinition(UUID.randomUUID().toString(), "中心", new CanvasNodeLayout(0d, 0d, 368d, 224d), c);
            boolean active = c.resultMode() != null || c.analyses() != null && c.analyses().stream().anyMatch(a -> a.outputTableName() != null);
            assertEquals(active, hasNodeIssue(CanvasGraphPlan.create(new CanvasDefinition(4, 30, List.of(node), List.of())), 0, "SPATIAL_CENTER_RESULTS_REQUIRE_SCHEMA_VERSION"));
            assertFalse(hasNodeIssue(CanvasGraphPlan.create(new CanvasDefinition(4, 31, List.of(node), List.of())), 0, "SPATIAL_CENTER_RESULTS_REQUIRE_SCHEMA_VERSION"));
        }
    }

    @Test
    void gatesNearestMatchingAt30IncludingInactiveSettings() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (String json : List.of("{}", "{\"matching\":null}", "{\"matching\":{}}",
                "{\"matching\":{\"semantics\":\"LEGACY_KNN\",\"connectionLines\":{\"enabled\":true}}}")) {
            var c = mapper.readValue(json, cn.superhuang.data.scalpel.contract.task.SpatialNearestConfiguration.class);
            var node = new cn.superhuang.data.scalpel.contract.task.SpatialNearestNodeDefinition(UUID.randomUUID().toString(), "最近邻", new CanvasNodeLayout(0d, 0d, 368d, 216d), c);
            assertEquals(c.matching() != null, hasNodeIssue(CanvasGraphPlan.create(new CanvasDefinition(4, 29, List.of(node), List.of())), 0, "SPATIAL_NEAREST_MATCHING_REQUIRE_SCHEMA_VERSION"));
            assertFalse(hasNodeIssue(CanvasGraphPlan.create(new CanvasDefinition(4, 30, List.of(node), List.of())), 0, "SPATIAL_NEAREST_MATCHING_REQUIRE_SCHEMA_VERSION"));
        }
    }

    @Test
    void gatesUnaryPoliciesAt29IncludingLegacyButKeepsMissingPoliciesReadable() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (String suffix : List.of("", ",\"geometryPolicy\":null", ",\"geometryPolicy\":\"LEGACY\"", ",\"geometryPolicy\":\"OUTPUT_XY\"")) {
            var config = mapper.readValue("{\"sourceTableName\":\"\"" + suffix + "}", cn.superhuang.data.scalpel.contract.task.GeometrySimplifyConfiguration.class);
            var node = new cn.superhuang.data.scalpel.contract.task.GeometrySimplifyNodeDefinition(UUID.randomUUID().toString(), "简化", new CanvasNodeLayout(0d, 0d, 320d, 200d), config);
            assertEquals(config.geometryPolicy() != null, hasNodeIssue(CanvasGraphPlan.create(new CanvasDefinition(4, 28, List.of(node), List.of())), 0, "GEOMETRY_UNARY_POLICY_REQUIRE_SCHEMA_VERSION"));
            assertFalse(hasNodeIssue(CanvasGraphPlan.create(new CanvasDefinition(4, 29, List.of(node), List.of())), 0, "GEOMETRY_UNARY_POLICY_REQUIRE_SCHEMA_VERSION"));
            var derive = new cn.superhuang.data.scalpel.contract.task.GeometryDeriveNodeDefinition(UUID.randomUUID().toString(), "派生", new CanvasNodeLayout(0d, 0d, 320d, 200d),
                    mapper.readValue("{\"derivations\":[{\"kind\":null" + suffix + "}]}", cn.superhuang.data.scalpel.contract.task.GeometryDeriveConfiguration.class));
            assertEquals(config.geometryPolicy() != null, hasNodeIssue(CanvasGraphPlan.create(new CanvasDefinition(4, 28, List.of(derive), List.of())), 0, "GEOMETRY_UNARY_POLICY_REQUIRE_SCHEMA_VERSION"));
        }
    }

    @Test
    void gatesAreaGeometryAt42EvenWhenInactive() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (String area : List.of("null","{}","{\"enabled\":false}","{\"bufferMode\":\"FIELD\"}")) {
            var c = mapper.readValue("{\"reconstruction\":{\"areaGeometry\":"+area+"}}", cn.superhuang.data.scalpel.contract.task.TrackReconstructConfiguration.class);
            var node = new cn.superhuang.data.scalpel.contract.task.TrackReconstructNodeDefinition(UUID.randomUUID().toString(),"重建",new CanvasNodeLayout(0d,0d,352d,216d),c);
            assertEquals(!area.equals("null"),hasNodeIssue(CanvasGraphPlan.create(new CanvasDefinition(4,41,List.of(node),List.of())),0,"TRACK_AREA_GEOMETRY_REQUIRE_SCHEMA_VERSION"));
            assertFalse(hasNodeIssue(CanvasGraphPlan.create(new CanvasDefinition(4,42,List.of(node),List.of())),0,"TRACK_AREA_GEOMETRY_REQUIRE_SCHEMA_VERSION"));
        }
    }

    @Test void gatesGeodesicAreaAt44EvenForInactiveBranches() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (String boundary : List.of("null", "{}", "{\"maximumSegmentLength\":-1}")) {
            var c = mapper.readValue("{\"reconstruction\":{\"semantics\":\"LEGACY_POINTS\",\"areaGeometry\":{\"enabled\":false,\"geodesicBoundary\":"+boundary+"}}}",
                    cn.superhuang.data.scalpel.contract.task.TrackReconstructConfiguration.class);
            var node = new cn.superhuang.data.scalpel.contract.task.TrackReconstructNodeDefinition(UUID.randomUUID().toString(),"重建",new CanvasNodeLayout(0d,0d,352d,216d),c);
            assertEquals(!boundary.equals("null"),hasNodeIssue(CanvasGraphPlan.create(new CanvasDefinition(4,43,List.of(node),List.of())),0,"TRACK_GEODESIC_AREA_REQUIRE_SCHEMA_VERSION"));
            assertFalse(hasNodeIssue(CanvasGraphPlan.create(new CanvasDefinition(4,44,List.of(node),List.of())),0,"TRACK_GEODESIC_AREA_REQUIRE_SCHEMA_VERSION"));
        }
    }

    @Test void gatesNonemptyBufferWindowsAt43EvenForInactiveBranches() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (String bindings : List.of("null","[]","[{\"name\":\"history\",\"sourceColumnName\":\"radius\",\"startOffset\":-3,\"endOffset\":-1,\"statistic\":\"MEAN\"}]")) {
            var c = mapper.readValue("{\"reconstruction\":{\"areaGeometry\":{\"enabled\":false,\"bufferMode\":\"FIELD\",\"windowBindings\":"+bindings+"}}}",cn.superhuang.data.scalpel.contract.task.TrackReconstructConfiguration.class);
            var node = new cn.superhuang.data.scalpel.contract.task.TrackReconstructNodeDefinition(UUID.randomUUID().toString(),"重建",new CanvasNodeLayout(0d,0d,352d,216d),c);
            assertEquals(bindings.startsWith("[{"),hasNodeIssue(CanvasGraphPlan.create(new CanvasDefinition(4,42,List.of(node),List.of())),0,"TRACK_BUFFER_WINDOWS_REQUIRE_SCHEMA_VERSION"));
            assertFalse(hasNodeIssue(CanvasGraphPlan.create(new CanvasDefinition(4,43,List.of(node),List.of())),0,"TRACK_BUFFER_WINDOWS_REQUIRE_SCHEMA_VERSION"));
        }
    }

    @Test
    void gatesExplicitPathAt28EvenWhenInactive() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (String json : List.of("{}", "{\"reconstruction\":{}}", "{\"reconstruction\":{\"pathGeometry\":null}}",
                "{\"reconstruction\":{\"pathGeometry\":{}}}",
                "{\"reconstruction\":{\"semantics\":\"LEGACY_POINTS\",\"pathGeometry\":{\"mode\":\"LEGACY_VERTEX_LINE\"}}}")) {
            var c = mapper.readValue(json, cn.superhuang.data.scalpel.contract.task.TrackReconstructConfiguration.class);
            var node = new cn.superhuang.data.scalpel.contract.task.TrackReconstructNodeDefinition(UUID.randomUUID().toString(), "重建",
                    new CanvasNodeLayout(0d, 0d, 352d, 216d), c);
            boolean hasPath = c.reconstruction() != null && c.reconstruction().pathGeometry() != null;
            assertEquals(hasPath, hasNodeIssue(CanvasGraphPlan.create(new CanvasDefinition(4, 27, List.of(node), List.of())), 0, "TRACK_PATH_GEOMETRY_REQUIRE_SCHEMA_VERSION"));
            assertFalse(hasNodeIssue(CanvasGraphPlan.create(new CanvasDefinition(4, 28, List.of(node), List.of())), 0, "TRACK_PATH_GEOMETRY_REQUIRE_SCHEMA_VERSION"));
        }
    }

    @Test
    void gatesReconstructionOptionsAt27IncludingInactiveSettings() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (String json : List.of("{}", "{\"reconstruction\":null}", "{\"reconstruction\":{}}",
                "{\"reconstruction\":{\"semantics\":\"LEGACY_POINTS\"}}")) {
            var config = mapper.readValue(json, cn.superhuang.data.scalpel.contract.task.TrackReconstructConfiguration.class);
            var node = new cn.superhuang.data.scalpel.contract.task.TrackReconstructNodeDefinition(
                    UUID.randomUUID().toString(), "重建", new CanvasNodeLayout(0d, 0d, 352d, 216d), config);
            var old = CanvasGraphPlan.create(new CanvasDefinition(4, 26, List.of(node), List.of()));
            assertEquals(config.reconstruction() != null, hasNodeIssue(old, 0, "TRACK_RECONSTRUCT_OPTIONS_REQUIRE_SCHEMA_VERSION"));
            var current = CanvasGraphPlan.create(new CanvasDefinition(4, 27, List.of(node), List.of()));
            assertFalse(hasNodeIssue(current, 0, "TRACK_RECONSTRUCT_OPTIONS_REQUIRE_SCHEMA_VERSION"));
        }
    }

    @Test
    void gatesOverlayModesAndExplicitPolicyAt26WithoutChangingLegacyNodes() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (String json : List.of("{\"operation\":\"INTERSECTION\"}",
                "{\"operation\":\"IDENTITY\"}", "{\"operation\":\"SYMMETRICAL_DIFFERENCE\"}",
                "{\"operation\":\"UNION\",\"geometryPolicy\":\"FAMILY_2D\"}",
                "{\"operation\":\"ERASE\",\"geometryPolicy\":\"LEGACY_GEOMETRY\"}")) {
            var config = mapper.readValue(json, cn.superhuang.data.scalpel.contract.task.SpatialOverlayConfiguration.class);
            var node = new cn.superhuang.data.scalpel.contract.task.SpatialOverlayNodeDefinition(
                    UUID.randomUUID().toString(), "叠加", new CanvasNodeLayout(0d, 0d, 376d, 216d), config);
            var old = CanvasGraphPlan.create(new CanvasDefinition(4, 25, List.of(node), List.of()));
            assertEquals(config.requiresFamilyGeometryVersion(), hasNodeIssue(old, 0, "SPATIAL_OVERLAY_FAMILY_REQUIRE_SCHEMA_VERSION"));
            var current = CanvasGraphPlan.create(new CanvasDefinition(4, 26, List.of(node), List.of()));
            assertFalse(hasNodeIssue(current, 0, "SPATIAL_OVERLAY_FAMILY_REQUIRE_SCHEMA_VERSION"));
        }
    }

    @Test
    void gatesLinkedGroupDraftsAt25EvenWhenGroupingIsInactive() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (String field : List.of("", ",\"groupResult\":null", ",\"groupResult\":{}",
                ",\"groupResult\":{\"mode\":\"LEGACY_FLAT\"}")) {
            var config = mapper.readValue("{\"includeEmptyAreas\":true" + field + "}",
                    cn.superhuang.data.scalpel.contract.task.SpatialSummarizeWithinConfiguration.class);
            var node = new cn.superhuang.data.scalpel.contract.task.SpatialSummarizeWithinNodeDefinition(
                    UUID.randomUUID().toString(), "分组", new CanvasNodeLayout(0d, 0d, 376d, 224d), config);
            var old = CanvasGraphPlan.create(new CanvasDefinition(4, 24, List.of(node), List.of()));
            if (config.groupResult() == null) assertFalse(hasNodeIssue(old, 0, "SPATIAL_WITHIN_GROUP_RESULT_REQUIRE_SCHEMA_VERSION"));
            else assertTrue(hasNodeIssueAtPath(old, 0, "SPATIAL_WITHIN_GROUP_RESULT_REQUIRE_SCHEMA_VERSION", "configuration.groupResult"));
            var current = CanvasGraphPlan.create(new CanvasDefinition(4, 25, List.of(node), List.of()));
            assertFalse(hasNodeIssue(current, 0, "SPATIAL_WITHIN_GROUP_RESULT_REQUIRE_SCHEMA_VERSION"));
        }
    }

    @Test
    void gatesExplicitWithinStatisticsAt24AndKeepsOriginalCountsAt12() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (String statistic : List.of("{\"kind\":\"COUNT\"}", "{\"kind\":\"COUNT_FIELD\"}",
                "{\"kind\":\"ANY\"}", "{\"kind\":\"SUM\",\"valueTreatment\":\"ORIGINAL_VALUE\"}",
                "{\"kind\":\"MEAN\",\"weighting\":\"INTERSECTION_FRACTION\"}")) {
            var config = mapper.readValue("{\"includeEmptyAreas\":true,\"statistics\":[" + statistic + "]}",
                    cn.superhuang.data.scalpel.contract.task.SpatialSummarizeWithinConfiguration.class);
            var node = new cn.superhuang.data.scalpel.contract.task.SpatialSummarizeWithinNodeDefinition(
                    UUID.randomUUID().toString(), "区域汇总", new CanvasNodeLayout(0d, 0d, 376d, 224d), config);
            var old = CanvasGraphPlan.create(new CanvasDefinition(4, 12, List.of(node), List.of()));
            if (config.usesExplicitStatistics()) assertTrue(hasNodeIssueAtPath(old, 0,
                    "SPATIAL_WITHIN_STATISTICS_REQUIRE_SCHEMA_VERSION", "configuration.statistics"));
            else assertFalse(hasNodeIssue(old, 0, "SPATIAL_WITHIN_STATISTICS_REQUIRE_SCHEMA_VERSION"));
            var current = CanvasGraphPlan.create(new CanvasDefinition(4, 24, List.of(node), List.of()));
            assertFalse(hasNodeIssue(current, 0, "SPATIAL_WITHIN_STATISTICS_REQUIRE_SCHEMA_VERSION"));
        }
    }

    @Test
    void gatesMotionWindowOptionsAt23() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (String fields : List.of("", ",\"motionSemantics\":\"OBSERVATION_WINDOW\"",
                ",\"windowOptions\":{\"observationCount\":3}")) {
            var config = mapper.readValue("{\"historyPoints\":1" + fields + "}",
                    cn.superhuang.data.scalpel.contract.task.TrackMotionStatisticsConfiguration.class);
            var node = new cn.superhuang.data.scalpel.contract.task.TrackMotionStatisticsNodeDefinition(
                    UUID.randomUUID().toString(), "运动", new CanvasNodeLayout(0d, 0d, 360d, 224d), config);
            var old = CanvasGraphPlan.create(new CanvasDefinition(4, fields.isEmpty() ? 15 : 22, List.of(node), List.of()));
            if (fields.isEmpty()) assertFalse(hasNodeIssue(old, 0, "TRACK_MOTION_WINDOW_REQUIRE_SCHEMA_VERSION"));
            else assertTrue(hasNodeIssue(old, 0, "TRACK_MOTION_WINDOW_REQUIRE_SCHEMA_VERSION"));
            var current = CanvasGraphPlan.create(new CanvasDefinition(4, 23, List.of(node), List.of()));
            assertFalse(hasNodeIssue(current, 0, "TRACK_MOTION_WINDOW_REQUIRE_SCHEMA_VERSION"));
        }
    }

    @Test
    void gatesDwellStrategyAt22WhileLegacyRemainsAvailableAt16() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (String fields : List.of("", ",\"dwellSemantics\":\"REFERENCE_CENTER\"",
                ",\"rangeOptions\":{\"resultMode\":\"ALL_FEATURES\"}")) {
            var config = mapper.readValue("{\"distanceThreshold\":1,\"minimumDuration\":1" + fields + "}",
                    cn.superhuang.data.scalpel.contract.task.TrackFindDwellConfiguration.class);
            var node = new cn.superhuang.data.scalpel.contract.task.TrackFindDwellNodeDefinition(
                    UUID.randomUUID().toString(), "驻留", new CanvasNodeLayout(0d, 0d, 352d, 216d), config);
            var old = CanvasGraphPlan.create(new CanvasDefinition(4, fields.isEmpty() ? 16 : 21, List.of(node), List.of()));
            if (fields.isEmpty()) assertFalse(hasNodeIssue(old, 0, "TRACK_DWELL_RANGE_REQUIRE_SCHEMA_VERSION"));
            else assertTrue(hasNodeIssue(old, 0, "TRACK_DWELL_RANGE_REQUIRE_SCHEMA_VERSION"));
            var current = CanvasGraphPlan.create(new CanvasDefinition(4, 22, List.of(node), List.of()));
            assertFalse(hasNodeIssue(current, 0, "TRACK_DWELL_RANGE_REQUIRE_SCHEMA_VERSION"));
        }
    }

    @Test
    void gatesOptionalTrackCapabilitiesWithoutChangingLegacyNodeIntroduction() {
        var configuration = new cn.superhuang.data.scalpel.contract.task.TrackDetectIncidentsConfiguration(
                "events", null, List.of("track"), "time", null,
                new cn.superhuang.data.scalpel.contract.task.TrackBoundaryConfiguration(null, null, null, null,
                        new cn.superhuang.data.scalpel.contract.task.TrackFixedTimeBoundary(1,
                                cn.superhuang.data.scalpel.contract.task.TrackTimeBoundaryUnit.DAYS, null, null)),
                null, null, null, "incidents", "id", "flag", "start", "end", "duration",
                cn.superhuang.data.scalpel.contract.task.SpatialDurationUnit.SECONDS,
                cn.superhuang.data.scalpel.contract.task.TrackIncidentSemantics.CONDITION_LIFECYCLE, "status", List.of());
        var node = new cn.superhuang.data.scalpel.contract.task.TrackDetectIncidentsNodeDefinition(
                UUID.randomUUID().toString(), "事件", new CanvasNodeLayout(0d, 0d, 320d, 200d), configuration);
        var old = CanvasGraphPlan.create(new CanvasDefinition(4, 20, List.of(node), List.of()));
        assertTrue(hasNodeIssueAtPath(old, 0, "TRACK_INCIDENT_OPTIONS_REQUIRE_SCHEMA_VERSION", "configuration.incidentSemantics"));
        assertTrue(hasNodeIssueAtPath(old, 0, "TRACK_TIME_BOUNDARY_REQUIRE_SCHEMA_VERSION", "configuration.boundaries.fixedTimeBoundary"));
        var current = CanvasGraphPlan.create(new CanvasDefinition(4, 21, List.of(node), List.of()));
        assertFalse(hasNodeIssue(current, 0, "TRACK_INCIDENT_OPTIONS_REQUIRE_SCHEMA_VERSION"));
        assertFalse(hasNodeIssue(current, 0, "TRACK_TIME_BOUNDARY_REQUIRE_SCHEMA_VERSION"));
    }

    @Test
    void allowsJoinToReceiveOneOrMoreTableMapInputs() {
        assertFalse(hasDegreeIssue(joinPlan(1), 3));
        assertFalse(hasDegreeIssue(joinPlan(2), 3));
        assertFalse(hasDegreeIssue(joinPlan(3), 3));
        assertTrue(hasDegreeIssue(joinPlan(0), 3));
    }

    @Test
    void appliesInputAndOutputDegreeRulesToModelNodes() {
        String inputId = UUID.randomUUID().toString();
        String outputId = UUID.randomUUID().toString();
        CanvasDefinition validTopology = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(
                        new ModelInputNodeDefinition(
                                inputId,
                                "模型输入",
                                new CanvasNodeLayout(0d, 0d, 240d, 120d),
                                modelInputConfiguration()
                        ),
                        new ModelOutputNodeDefinition(
                                outputId,
                                "模型输出",
                                new CanvasNodeLayout(320d, 0d, 240d, 120d),
                                modelOutputConfiguration("orders", null, List.of())
                        )
                ),
                List.of(new CanvasEdgeDefinition(UUID.randomUUID().toString(), inputId, outputId))
        );

        CanvasGraphPlan validPlan = CanvasGraphPlan.create(validTopology);
        assertFalse(hasDegreeIssue(validPlan, 0));
        assertFalse(hasDegreeIssue(validPlan, 1));

        CanvasGraphPlan invalidPlan = CanvasGraphPlan.create(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                validTopology.nodes(),
                List.of(new CanvasEdgeDefinition(UUID.randomUUID().toString(), outputId, inputId))
        ));
        assertTrue(hasDegreeIssue(invalidPlan, 0));
        assertTrue(hasDegreeIssue(invalidPlan, 1));
    }

    @Test
    void acceptsOnlyCurrentCanvasMajorAndMinorVersion() {
        CanvasGraphPlan current = CanvasGraphPlan.create(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(),
                List.of()
        ));
        CanvasGraphPlan previousMajor = CanvasGraphPlan.create(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION - 1,
                3,
                List.of(),
                List.of()
        ));
        CanvasGraphPlan legacyMajor = CanvasGraphPlan.create(new CanvasDefinition(
                1,
                28,
                List.of(),
                List.of()
        ));
        CanvasGraphPlan futureMinor = CanvasGraphPlan.create(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION + 1,
                List.of(),
                List.of()
        ));
        CanvasGraphPlan futureMajor = CanvasGraphPlan.create(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION + 1,
                0,
                List.of(),
                List.of()
        ));

        assertFalse(hasCanvasIssue(current, "UNSUPPORTED_SCHEMA_VERSION"));
        assertFalse(hasCanvasIssue(current, "UNSUPPORTED_SCHEMA_MINOR_VERSION"));
        assertTrue(hasCanvasIssue(previousMajor, "UNSUPPORTED_SCHEMA_VERSION"));
        assertTrue(hasCanvasIssue(legacyMajor, "UNSUPPORTED_SCHEMA_VERSION"));
        assertTrue(hasCanvasIssue(futureMinor, "UNSUPPORTED_SCHEMA_MINOR_VERSION"));
        assertTrue(hasCanvasIssue(futureMajor, "UNSUPPORTED_SCHEMA_VERSION"));
    }

    @Test
    void rejectsEpochTimestampUnitsInDefinitionsBeforeCanvasFourDotTwo() {
        TypeCastNodeDefinition typeCast = new TypeCastNodeDefinition(
                UUID.randomUUID().toString(),
                "毫秒时间戳转换",
                new CanvasNodeLayout(0d, 0d, 240d, 120d),
                new TypeCastConfiguration(List.of(new TypeCastOperation(
                        UUID.randomUUID().toString(),
                        "events",
                        new ProcessorOutput.CreateNewTable("typed_events"),
                        List.of(new ColumnTypeCast(
                                "event_time_ms",
                                new PlatformTypeDefinition(PlatformDataType.TIMESTAMP, null, null, null, null),
                                CastFailureStrategy.FAIL,
                                EpochTimestampUnit.MILLISECONDS
                        ))
                )))
        );

        CanvasGraphPlan fourDotOne = CanvasGraphPlan.create(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                1,
                List.of(typeCast),
                List.of()
        ));
        CanvasGraphPlan current = CanvasGraphPlan.create(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(typeCast),
                List.of()
        ));

        assertTrue(hasNodeIssueAtPath(
                fourDotOne,
                0,
                "EPOCH_TIMESTAMP_UNIT_SCHEMA_MINOR_VERSION_NOT_SUPPORTED",
                "configuration.operations[0].casts[0].epochTimestampUnit"
        ));
        assertFalse(hasNodeIssue(current, 0, "EPOCH_TIMESTAMP_UNIT_SCHEMA_MINOR_VERSION_NOT_SUPPORTED"));
    }

    @Test
    void rejectsStringTemporalParsingInDefinitionsBeforeCanvasFourDotThree() {
        TypeCastNodeDefinition typeCast = new TypeCastNodeDefinition(
                UUID.randomUUID().toString(),
                "字符串时间转换",
                new CanvasNodeLayout(0d, 0d, 240d, 120d),
                new TypeCastConfiguration(List.of(new TypeCastOperation(
                        UUID.randomUUID().toString(),
                        "events",
                        new ProcessorOutput.CreateNewTable("typed_events"),
                        List.of(new ColumnTypeCast(
                                "created_at_text",
                                new PlatformTypeDefinition(PlatformDataType.TIMESTAMP, null, null, null, null),
                                CastFailureStrategy.FAIL,
                                null,
                                new cn.superhuang.data.scalpel.contract.task.StringTemporalParseOptions(
                                        "yyyy-MM-dd HH:mm:ss",
                                        cn.superhuang.data.scalpel.contract.task.StringTimestampZoneMode.SOURCE_TIME_ZONE,
                                        "Asia/Shanghai"
                                )
                        ))
                )))
        );

        CanvasGraphPlan fourDotTwo = CanvasGraphPlan.create(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                2,
                List.of(typeCast),
                List.of()
        ));
        CanvasGraphPlan current = CanvasGraphPlan.create(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(typeCast),
                List.of()
        ));

        assertTrue(hasNodeIssueAtPath(
                fourDotTwo,
                0,
                "STRING_TEMPORAL_PARSE_SCHEMA_MINOR_VERSION_NOT_SUPPORTED",
                "configuration.operations[0].casts[0].stringTemporalParseOptions"
        ));
        assertFalse(hasNodeIssue(current, 0, "STRING_TEMPORAL_PARSE_SCHEMA_MINOR_VERSION_NOT_SUPPORTED"));
    }

    @Test
    void rejectsTemporalStringFormattingInDefinitionsBeforeCanvasFourDotSeven() {
        TypeCastNodeDefinition typeCast = new TypeCastNodeDefinition(
                UUID.randomUUID().toString(),
                "时间字符串格式化",
                new CanvasNodeLayout(0d, 0d, 240d, 120d),
                new TypeCastConfiguration(List.of(new TypeCastOperation(
                        UUID.randomUUID().toString(),
                        "events",
                        new ProcessorOutput.CreateNewTable("typed_events"),
                        List.of(new ColumnTypeCast(
                                "created_at",
                                new PlatformTypeDefinition(PlatformDataType.STRING, null, null, null, null),
                                CastFailureStrategy.FAIL,
                                null,
                                null,
                                new cn.superhuang.data.scalpel.contract.task.TemporalStringFormatOptions(
                                        "yyyy-MM-dd HH:mm:ss", "UTC"
                                )
                        ))
                )))
        );

        CanvasGraphPlan fourDotSix = CanvasGraphPlan.create(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                6,
                List.of(typeCast),
                List.of()
        ));
        CanvasGraphPlan current = CanvasGraphPlan.create(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(typeCast),
                List.of()
        ));

        assertTrue(hasNodeIssueAtPath(
                fourDotSix,
                0,
                "TEMPORAL_STRING_FORMAT_SCHEMA_MINOR_VERSION_NOT_SUPPORTED",
                "configuration.operations[0].casts[0].temporalStringFormatOptions"
        ));
        assertFalse(hasNodeIssue(
                current,
                0,
                "TEMPORAL_STRING_FORMAT_SCHEMA_MINOR_VERSION_NOT_SUPPORTED"
        ));
    }

    @Test
    void rejectsTemporalToEpochLongInDefinitionsBeforeCanvasFourDotEight() {
        TypeCastNodeDefinition typeCast = new TypeCastNodeDefinition(
                UUID.randomUUID().toString(),
                "时间转 Epoch",
                new CanvasNodeLayout(0d, 0d, 240d, 120d),
                new TypeCastConfiguration(List.of(new TypeCastOperation(
                        UUID.randomUUID().toString(),
                        "events",
                        new ProcessorOutput.CreateNewTable("typed_events"),
                        List.of(new ColumnTypeCast(
                                "created_at",
                                new PlatformTypeDefinition(PlatformDataType.LONG, null, null, null, null),
                                CastFailureStrategy.FAIL,
                                EpochTimestampUnit.MILLISECONDS
                        ))
                )))
        );

        CanvasGraphPlan fourDotSeven = CanvasGraphPlan.create(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                7,
                List.of(typeCast),
                List.of()
        ));
        CanvasGraphPlan current = CanvasGraphPlan.create(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(typeCast),
                List.of()
        ));

        assertTrue(hasNodeIssueAtPath(
                fourDotSeven,
                0,
                "TEMPORAL_TO_EPOCH_SCHEMA_MINOR_VERSION_NOT_SUPPORTED",
                "configuration.operations[0].casts[0].epochTimestampUnit"
        ));
        assertFalse(hasNodeIssue(
                current,
                0,
                "TEMPORAL_TO_EPOCH_SCHEMA_MINOR_VERSION_NOT_SUPPORTED"
        ));
    }

    @Test
    void allowsTerminalSpatialProcessorsWithAnUnusedOutputWarning() {
        String leftInputId = UUID.randomUUID().toString();
        String rightInputId = UUID.randomUUID().toString();
        String clipId = UUID.randomUUID().toString();
        String clipOutputId = UUID.randomUUID().toString();
        CanvasDefinition clipDefinition = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(
                        modelInput(leftInputId, "来源输入"),
                        modelInput(rightInputId, "Mask 输入"),
                        new SpatialClipNodeDefinition(
                                clipId,
                                "空间裁剪",
                                new CanvasNodeLayout(320d, 0d, 240d, 120d),
                                new SpatialClipConfiguration(
                                        "roads", "districts", "clipped",
                                        "shape", "boundary", "clipped_shape")
                        ),
                        modelOutput(clipOutputId, "clipped")
                ),
                List.of(
                        new CanvasEdgeDefinition(UUID.randomUUID().toString(), leftInputId, clipId),
                        new CanvasEdgeDefinition(UUID.randomUUID().toString(), rightInputId, clipId),
                        new CanvasEdgeDefinition(UUID.randomUUID().toString(), clipId, clipOutputId)
                )
        );
        CanvasGraphPlan validClip = CanvasGraphPlan.create(clipDefinition);
        assertFalse(hasDegreeIssue(validClip, 2));
        CanvasGraphPlan invalidClip = CanvasGraphPlan.create(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                clipDefinition.nodes(),
                clipDefinition.edges().subList(0, 2)));
        assertFalse(hasDegreeIssue(invalidClip, 2));
        assertTrue(hasNodeWarning(invalidClip, 2, "UNCONSUMED_PROCESSOR_OUTPUT"));

        String aggregateInputId = UUID.randomUUID().toString();
        String aggregateId = UUID.randomUUID().toString();
        String aggregateOutputId = UUID.randomUUID().toString();
        CanvasDefinition aggregateDefinition = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(
                        modelInput(aggregateInputId, "来源输入"),
                        new SpatialAggregateNodeDefinition(
                                aggregateId,
                                "空间聚合",
                                new CanvasNodeLayout(320d, 0d, 240d, 120d),
                                new SpatialAggregateConfiguration(
                                        "parcels", "districts", List.of(), List.of())
                        ),
                        modelOutput(aggregateOutputId, "districts")
                ),
                List.of(
                        new CanvasEdgeDefinition(
                                UUID.randomUUID().toString(), aggregateInputId, aggregateId),
                        new CanvasEdgeDefinition(
                                UUID.randomUUID().toString(), aggregateId, aggregateOutputId)
                )
        );
        CanvasGraphPlan validAggregate = CanvasGraphPlan.create(aggregateDefinition);
        assertFalse(hasDegreeIssue(validAggregate, 1));
    }

    @Test
    void allowsRenameToMergeMultipleInputTableMaps() {
        String inputId = UUID.randomUUID().toString();
        String secondInputId = UUID.randomUUID().toString();
        String renameId = UUID.randomUUID().toString();
        String outputId = UUID.randomUUID().toString();
        CanvasDefinition definition = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(
                        new ModelInputNodeDefinition(
                                inputId,
                                "模型输入",
                                new CanvasNodeLayout(0d, 0d, 240d, 120d),
                                modelInputConfiguration()
                        ),
                        new ModelInputNodeDefinition(
                                secondInputId,
                                "第二个模型输入",
                                new CanvasNodeLayout(0d, 180d, 240d, 120d),
                                modelInputConfiguration()
                        ),
                        new RenameNodeDefinition(
                                renameId,
                                "重命名",
                                new CanvasNodeLayout(320d, 0d, 240d, 120d),
                                new RenameConfiguration("orders", "source_orders", List.of())
                        ),
                        new ModelOutputNodeDefinition(
                                outputId,
                                "模型输出",
                                new CanvasNodeLayout(640d, 0d, 240d, 120d),
                                modelOutputConfiguration("source_orders", null, List.of())
                        )
                ),
                List.of(
                        new CanvasEdgeDefinition(UUID.randomUUID().toString(), inputId, renameId),
                        new CanvasEdgeDefinition(UUID.randomUUID().toString(), secondInputId, renameId),
                        new CanvasEdgeDefinition(UUID.randomUUID().toString(), renameId, outputId)
                )
        );

        CanvasGraphPlan plan = CanvasGraphPlan.create(definition);

        assertFalse(hasDegreeIssue(plan, 2));
    }

    @Test
    void rejectsOnlyStreamingOverwriteAfterWriteModeIsConfigured() {
        CanvasGraphPlan unconfigured = streamingJdbcOutputPlan(null);
        CanvasGraphPlan overwrite = streamingJdbcOutputPlan(JdbcWriteMode.OVERWRITE);
        CanvasGraphPlan upsert = streamingJdbcOutputPlan(JdbcWriteMode.UPSERT);

        assertFalse(hasNodeIssue(unconfigured, 1, "STREAMING_JDBC_OUTPUT_OVERWRITE_NOT_SUPPORTED"));
        assertTrue(hasNodeIssue(overwrite, 1, "STREAMING_JDBC_OUTPUT_OVERWRITE_NOT_SUPPORTED"));
        assertFalse(hasNodeIssue(upsert, 1, "STREAMING_JDBC_OUTPUT_OVERWRITE_NOT_SUPPORTED"));
    }

    @Test
    void rejectsOverwriteInTheSecondStreamingOutputWriteWithIndexedPath() {
        CanvasGraphPlan jdbc = streamingJdbcOutputPlanWithModes(
                JdbcWriteMode.APPEND, JdbcWriteMode.OVERWRITE);
        CanvasGraphPlan model = streamingModelOutputPlanWithModes(
                JdbcWriteMode.APPEND, JdbcWriteMode.OVERWRITE);

        assertTrue(hasNodeIssueAtPath(
                jdbc, 1, "STREAMING_JDBC_OUTPUT_OVERWRITE_NOT_SUPPORTED",
                "configuration.writes[1].writeMode"));
        assertTrue(hasNodeIssueAtPath(
                model, 1, "STREAMING_MODEL_OUTPUT_OVERWRITE_NOT_SUPPORTED",
                "configuration.writes[1].writeMode"));
    }

    @Test
    void treatsModelOutputUpsertAsAStreamingOutputInCurrentCanvasVersion() {
        String inputId = UUID.randomUUID().toString();
        String outputId = UUID.randomUUID().toString();
        ModelOutputNodeDefinition output = new ModelOutputNodeDefinition(
                outputId,
                "模型输出",
                new CanvasNodeLayout(320d, 0d, 240d, 120d),
                modelOutputConfiguration(
                        "order_events", JdbcWriteMode.UPSERT,
                        List.of(new JdbcColumnMapping("id", "id")))
        );
        CanvasDefinition current = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(
                        new KafkaInputNodeDefinition(
                                inputId,
                                "Kafka 输入",
                                new CanvasNodeLayout(0d, 0d, 240d, 120d),
                                new KafkaInputConfiguration(
                                        UUID.randomUUID().toString(),
                                        "order-events",
                                        new KafkaValueSchema(List.of()),
                                        "order_events",
                                        KafkaStartingOffsets.LATEST
                                )
                        ),
                        output
                ),
                List.of(new CanvasEdgeDefinition(UUID.randomUUID().toString(), inputId, outputId))
        );
        CanvasGraphPlan streaming = CanvasGraphPlan.create(current, CanvasExecutionMode.STREAMING);

        assertFalse(hasNodeIssue(streaming, 1, "NODE_EXECUTION_MODE_NOT_SUPPORTED"));
        assertFalse(hasCanvasIssue(streaming, "STREAMING_OUTPUT_REQUIRED"));
    }

    @Test
    void rejectsModelOverwriteButAllowsModelUpsertInStreamingMode() {
        CanvasGraphPlan overwrite = streamingModelOutputPlan(JdbcWriteMode.OVERWRITE);
        CanvasGraphPlan upsert = streamingModelOutputPlan(JdbcWriteMode.UPSERT);

        assertTrue(hasNodeIssue(overwrite, 1, "STREAMING_MODEL_OUTPUT_OVERWRITE_NOT_SUPPORTED"));
        assertFalse(hasNodeIssue(upsert, 1, "STREAMING_MODEL_OUTPUT_OVERWRITE_NOT_SUPPORTED"));
    }

    private static CanvasGraphPlan streamingJdbcOutputPlan(JdbcWriteMode writeMode) {
        return streamingJdbcOutputPlanWithModes(writeMode);
    }

    private static CanvasGraphPlan joinPlan(int incomingCount) {
        String firstInputId = UUID.randomUUID().toString();
        String secondInputId = UUID.randomUUID().toString();
        String thirdInputId = UUID.randomUUID().toString();
        String joinId = UUID.randomUUID().toString();
        String outputId = UUID.randomUUID().toString();
        List<String> inputIds = List.of(firstInputId, secondInputId, thirdInputId);
        List<CanvasEdgeDefinition> edges = new java.util.ArrayList<>();
        for (int index = 0; index < incomingCount; index++) {
            edges.add(new CanvasEdgeDefinition(
                    UUID.randomUUID().toString(), inputIds.get(index), joinId));
        }
        edges.add(new CanvasEdgeDefinition(UUID.randomUUID().toString(), joinId, outputId));
        return CanvasGraphPlan.create(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(
                        modelInput(firstInputId, "输入一"),
                        modelInput(secondInputId, "输入二"),
                        modelInput(thirdInputId, "输入三"),
                        new JoinNodeDefinition(
                                joinId,
                                "订单客户 Join",
                                new CanvasNodeLayout(320d, 0d, 360d, 216d),
                                new JoinConfiguration(
                                        "orders",
                                        "customers",
                                        "order_customers",
                                        JoinType.INNER,
                                        List.of(new JoinCondition(
                                                "customer_id", JoinOperator.EQUALS, "id")))),
                        modelOutput(outputId, "order_customers")
                ),
                edges
        ));
    }

    private static CanvasGraphPlan streamingJdbcOutputPlanWithModes(JdbcWriteMode... writeModes) {
        String inputId = UUID.randomUUID().toString();
        String outputId = UUID.randomUUID().toString();
        return CanvasGraphPlan.create(
                new CanvasDefinition(
                        CanvasDefinition.CURRENT_SCHEMA_VERSION,
                        CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                        List.of(
                                new KafkaInputNodeDefinition(
                                        inputId,
                                        "Kafka 输入",
                                        new CanvasNodeLayout(0d, 0d, 240d, 120d),
                                        new KafkaInputConfiguration(
                                                UUID.randomUUID().toString(),
                                                "order-events",
                                                new KafkaValueSchema(List.of()),
                                                "order_events",
                                                KafkaStartingOffsets.LATEST
                                        )
                                ),
                                new JdbcOutputNodeDefinition(
                                        outputId,
                                        "JDBC 输出",
                                        new CanvasNodeLayout(320d, 0d, 240d, 120d),
                                        new JdbcOutputConfiguration(
                                                UUID.randomUUID().toString(),
                                                java.util.stream.IntStream.range(0, writeModes.length)
                                                        .mapToObj(index -> new JdbcOutputWrite(
                                                                UUID.randomUUID().toString(),
                                                                "order_events", "order_events_" + index,
                                                                writeModes[index], List.of(), List.of()))
                                                        .toList())
                                )
                        ),
                        List.of(new CanvasEdgeDefinition(UUID.randomUUID().toString(), inputId, outputId))
                ),
                CanvasExecutionMode.STREAMING
        );
    }

    private static CanvasGraphPlan streamingModelOutputPlan(JdbcWriteMode writeMode) {
        return streamingModelOutputPlanWithModes(writeMode);
    }

    private static CanvasGraphPlan streamingModelOutputPlanWithModes(JdbcWriteMode... writeModes) {
        String inputId = UUID.randomUUID().toString();
        String outputId = UUID.randomUUID().toString();
        return CanvasGraphPlan.create(
                new CanvasDefinition(
                        CanvasDefinition.CURRENT_SCHEMA_VERSION,
                        CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                        List.of(
                                new KafkaInputNodeDefinition(
                                        inputId,
                                        "Kafka 输入",
                                        new CanvasNodeLayout(0d, 0d, 240d, 120d),
                                        new KafkaInputConfiguration(
                                                UUID.randomUUID().toString(),
                                                "order-events",
                                                new KafkaValueSchema(List.of()),
                                                "order_events",
                                                KafkaStartingOffsets.LATEST
                                        )
                                ),
                                new ModelOutputNodeDefinition(
                                        outputId,
                                        "模型输出",
                                        new CanvasNodeLayout(320d, 0d, 240d, 120d),
                                        new ModelOutputConfiguration(
                                                java.util.Arrays.stream(writeModes)
                                                        .map(writeMode -> new ModelOutputWrite(
                                                                UUID.randomUUID().toString(),
                                                                "order_events", UUID.randomUUID().toString(),
                                                                writeMode, List.of()))
                                                        .toList())
                                )
                        ),
                        List.of(new CanvasEdgeDefinition(UUID.randomUUID().toString(), inputId, outputId))
                ),
                CanvasExecutionMode.STREAMING
        );
    }

    @Test
    void extendedUnitsHavePreciseVersionGateEvenInInactiveSettings() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var c = mapper.readValue("""
                {"nearestCount":1,"includeUnmatched":false,"maximumDistanceUnit":"FEET_US","distanceOutputUnit":"FEET",
                 "matching":{"semantics":"LEGACY_KNN","connectionLines":{"enabled":false,"maximumGeodesicSegmentLengthUnit":"YARDS_US"}}}
                """, cn.superhuang.data.scalpel.contract.task.SpatialNearestConfiguration.class);
        var node = new cn.superhuang.data.scalpel.contract.task.SpatialNearestNodeDefinition(UUID.randomUUID().toString(),
                "FEET_US", new CanvasNodeLayout(0d, 0d, 320d, 200d), c);
        String code = "SPATIAL_EXTENDED_UNITS_REQUIRE_SCHEMA_VERSION";
        var old = CanvasGraphPlan.create(new CanvasDefinition(4, 35, List.of(node), List.of()));
        assertTrue(hasNodeIssueAtPath(old, 0, code, "configuration.maximumDistanceUnit"));
        assertTrue(hasNodeIssueAtPath(old, 0, code, "configuration.matching.connectionLines.maximumGeodesicSegmentLengthUnit"));
        assertFalse(hasNodeIssueAtPath(old, 0, code, "configuration.distanceOutputUnit"));
        assertFalse(hasNodeIssue(CanvasGraphPlan.create(new CanvasDefinition(4, 36, List.of(node), List.of())), 0, code));
    }

    @Test void weightedDispersionRequires37WithoutBlockingOldMeanAndUnweightedVariance() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var config = mapper.readValue("""
                {"includeEmptyAreas":false,"statistics":[
                 {"kind":"VARIANCE","weighting":"INTERSECTION_FRACTION"},
                 {"kind":"STDDEV","weighting":"INTERSECTION_FRACTION"},
                 {"kind":"MEAN","weighting":"INTERSECTION_FRACTION"}, {"kind":"VARIANCE"}]}
                """, cn.superhuang.data.scalpel.contract.task.SpatialSummarizeWithinConfiguration.class);
        var node = new cn.superhuang.data.scalpel.contract.task.SpatialSummarizeWithinNodeDefinition(UUID.randomUUID().toString(),
                "区域汇总", new CanvasNodeLayout(0d, 0d, 320d, 200d), config);
        String code = "SPATIAL_WITHIN_WEIGHTED_DISPERSION_REQUIRE_SCHEMA_VERSION";
        var old = CanvasGraphPlan.create(new CanvasDefinition(4, 36, List.of(node), List.of()));
        assertTrue(hasNodeIssueAtPath(old, 0, code, "configuration.statistics[0].weighting"));
        assertTrue(hasNodeIssueAtPath(old, 0, code, "configuration.statistics[1].weighting"));
        assertFalse(hasNodeIssueAtPath(old, 0, code, "configuration.statistics[2].weighting"));
        assertFalse(hasNodeIssueAtPath(old, 0, code, "configuration.statistics[3].weighting"));
        assertFalse(hasNodeIssue(CanvasGraphPlan.create(new CanvasDefinition(4, 37, List.of(node), List.of())), 0, code));
    }

    private static boolean hasDegreeIssue(CanvasGraphPlan plan, int entryIndex) {
        return hasNodeIssue(plan, entryIndex, "INVALID_NODE_DEGREE");
    }

    @Test void planarGridRequires38EvenWhenHiddenByH3() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (String shape : List.of("SQUARE", "HEXAGON", "H3")) {
            var config = mapper.readValue("{\"statistics\":[],\"binShape\":\"" + shape + "\",\"planarGrid\":{\"originX\":0,\"originY\":0}}",
                    cn.superhuang.data.scalpel.contract.task.SpatialBinAggregateConfiguration.class);
            var node = new cn.superhuang.data.scalpel.contract.task.SpatialBinAggregateNodeDefinition(UUID.randomUUID().toString(), "格网",
                    new CanvasNodeLayout(0d, 0d, 360d, 224d), config);
            String code = "SPATIAL_PLANAR_GRID_REQUIRE_SCHEMA_VERSION";
            assertTrue(hasNodeIssueAtPath(CanvasGraphPlan.create(new CanvasDefinition(4, 37, List.of(node), List.of())), 0, code, "configuration.planarGrid"));
            assertFalse(hasNodeIssue(CanvasGraphPlan.create(new CanvasDefinition(4, 38, List.of(node), List.of())), 0, code));
        }
    }

    @Test void calendarOptionsRequireMinor39EvenWhenInactiveForBothNodes() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (String mode : List.of("CALENDAR", "FIXED_DURATION")) {
            String json = "{\"statistics\":[],\"temporalSlicing\":{\"interval\":1,\"intervalUnit\":\"HOURS\",\"calendar\":{\"mode\":\"" + mode + "\",\"intervalUnit\":\"MONTHS\"}}}";
            var bin = new cn.superhuang.data.scalpel.contract.task.SpatialBinAggregateNodeDefinition(UUID.randomUUID().toString(), "格网", new CanvasNodeLayout(0d, 0d, 360d, 224d),
                    mapper.readValue(json, cn.superhuang.data.scalpel.contract.task.SpatialBinAggregateConfiguration.class));
            var within = new cn.superhuang.data.scalpel.contract.task.SpatialSummarizeWithinNodeDefinition(UUID.randomUUID().toString(), "区域", new CanvasNodeLayout(0d, 0d, 360d, 224d),
                    mapper.readValue(json, cn.superhuang.data.scalpel.contract.task.SpatialSummarizeWithinConfiguration.class));
            for (cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition node : List.of(bin, within)) {
                String code = "SPATIAL_CALENDAR_WINDOW_REQUIRE_SCHEMA_VERSION";
                assertTrue(hasNodeIssueAtPath(CanvasGraphPlan.create(new CanvasDefinition(4, 38, List.of(node), List.of())), 0, code, "configuration.temporalSlicing.calendar"));
                assertFalse(hasNodeIssue(CanvasGraphPlan.create(new CanvasDefinition(4, 39, List.of(node), List.of())), 0, code));
            }
        }
    }

    @Test void explicitDbscanOptionsRequire40IncludingInactiveAlgorithmsAndModes() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (String algorithm : List.of("DBSCAN", "HDBSCAN", "MULTI_SCALE")) {
            for (String mode : List.of("LEGACY_SPATIAL", "SPATIAL", "LINEAR")) {
                var config = mapper.readValue("{\"parameters\":{\"algorithm\":\"" + algorithm + "\",\"minimumFeatures\":5},\"dbscan\":{\"mode\":\"" + mode + "\",\"timeColumnName\":\"time\"}}",
                        cn.superhuang.data.scalpel.contract.task.SpatialPointClusterConfiguration.class);
                var node = new cn.superhuang.data.scalpel.contract.task.SpatialPointClusterNodeDefinition(UUID.randomUUID().toString(), "聚类", new CanvasNodeLayout(0d,0d,352d,216d), config);
                String code = "SPATIAL_DBSCAN_OPTIONS_REQUIRE_SCHEMA_VERSION";
                assertTrue(hasNodeIssueAtPath(CanvasGraphPlan.create(new CanvasDefinition(4, 39, List.of(node), List.of())), 0, code, "configuration.dbscan"));
                assertFalse(hasNodeIssue(CanvasGraphPlan.create(new CanvasDefinition(4, 40, List.of(node), List.of())), 0, code));
            }
        }
    }

    @Test void withinRegionsRequire41EvenWhenGridIsInactive() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (String mode : List.of("AREA_TABLE", "PLANAR_GRID")) {
            var c = mapper.readValue("{\"regions\":{\"mode\":\"" + mode + "\"}}", cn.superhuang.data.scalpel.contract.task.SpatialSummarizeWithinConfiguration.class);
            var node = new cn.superhuang.data.scalpel.contract.task.SpatialSummarizeWithinNodeDefinition(UUID.randomUUID().toString(), "汇总", new CanvasNodeLayout(0d,0d,376d,224d), c);
            String code = "SPATIAL_WITHIN_REGIONS_REQUIRE_SCHEMA_VERSION";
            assertTrue(hasNodeIssueAtPath(CanvasGraphPlan.create(new CanvasDefinition(4,40,List.of(node),List.of())),0,code,"configuration.regions"));
            assertFalse(hasNodeIssue(CanvasGraphPlan.create(new CanvasDefinition(4,41,List.of(node),List.of())),0,code));
        }
    }

    private static ModelInputNodeDefinition modelInput(String id, String name) {
        return new ModelInputNodeDefinition(
                id,
                name,
                new CanvasNodeLayout(0d, 0d, 240d, 120d),
                modelInputConfiguration()
        );
    }

    private static ModelOutputNodeDefinition modelOutput(String id, String sourceTableName) {
        return new ModelOutputNodeDefinition(
                id,
                "输出",
                new CanvasNodeLayout(640d, 0d, 240d, 120d),
                modelOutputConfiguration(sourceTableName, null, List.of())
        );
    }

    private static ModelInputConfiguration modelInputConfiguration() {
        return new ModelInputConfiguration(List.of(
                new ModelInputSelection(UUID.randomUUID().toString())));
    }

    private static ModelOutputConfiguration modelOutputConfiguration(
            String sourceTableName,
            JdbcWriteMode writeMode,
            List<JdbcColumnMapping> mappings
    ) {
        return new ModelOutputConfiguration(List.of(new ModelOutputWrite(
                UUID.randomUUID().toString(), sourceTableName,
                UUID.randomUUID().toString(), writeMode, mappings)));
    }

    private static boolean hasNodeIssue(CanvasGraphPlan plan, int entryIndex, String code) {
        return plan.entries().get(entryIndex).result().result().issues().stream()
                .anyMatch(issue -> issue.code().equals(code));
    }

    private static boolean hasNodeWarning(CanvasGraphPlan plan, int entryIndex, String code) {
        return plan.entries().get(entryIndex).result().result().issues().stream()
                .anyMatch(issue -> issue.code().equals(code)
                        && issue.severity() == CompilationSeverity.WARNING);
    }

    private static boolean hasNodeIssueAtPath(
            CanvasGraphPlan plan,
            int entryIndex,
            String code,
            String path
    ) {
        return plan.entries().get(entryIndex).result().result().issues().stream()
                .anyMatch(issue -> issue.code().equals(code) && issue.path().equals(path));
    }

    private static boolean hasCanvasIssue(CanvasGraphPlan plan, String code) {
        return plan.canvasIssues().stream().anyMatch(issue -> issue.code().equals(code));
    }

}
