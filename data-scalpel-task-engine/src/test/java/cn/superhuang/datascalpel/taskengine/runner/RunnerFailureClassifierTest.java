package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.execution.ExecutionErrorCategory;
import cn.superhuang.data.scalpel.contract.execution.ExecutionFailurePhase;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionError;
import org.apache.spark.SparkException;
import org.apache.spark.sql.AnalysisException;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.TopologyException;
import org.locationtech.jts.io.ParseException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.file.FileAlreadyExistsException;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunnerFailureClassifierTest {
    @Test
    void describeDatasetSpatialFailuresUseStableFallback() {
        var error = new RunnerFailureClassifier().classify(
                new TopologyException("private-coordinate"),
                new RunnerFailureContext(
                        "node", "SPATIAL_DESCRIBE_DATASET", "描述数据集",
                        ExecutionFailurePhase.PROCESS, null));

        assertEquals("SPATIAL_DESCRIBE_DATASET_FAILED", error.code());
        assertEquals(ExecutionErrorCategory.SCHEMA, error.category());
        assertFalse(error.retryable());
        assertFalse(error.message().contains("private-coordinate"));
    }

    @Test void similarLocationsDataFailuresAreStableAndSafe() {
        for (String code : java.util.List.of(
                "SPATIAL_SIMILAR_LOCATIONS_REFERENCE_ID_INVALID",
                "SPATIAL_SIMILAR_LOCATIONS_CANDIDATE_ID_DUPLICATE",
                "SPATIAL_SIMILAR_LOCATIONS_REFERENCE_REQUIRED",
                "SPATIAL_SIMILAR_LOCATIONS_VALUE_INVALID",
                "SPATIAL_SIMILAR_LOCATIONS_ZERO_PROFILE",
                "SPATIAL_SIMILAR_LOCATIONS_SCORE_INVALID")) {
            var error = new RunnerFailureClassifier().classify(
                    new SparkException("wrapper",
                            new IllegalArgumentException(code + " private-id private-value")),
                    new RunnerFailureContext(
                            "node", "SPATIAL_SIMILAR_LOCATIONS", "查找相似位置",
                            ExecutionFailurePhase.PROCESS, null));
            assertEquals(code, error.code());
            assertEquals(ExecutionErrorCategory.SCHEMA, error.category());
            assertFalse(error.retryable());
            assertFalse(error.message().contains("private-"));
        }
    }

    @Test void hotSpotValueAndCapacityFailuresAreSafeAndNotRetryable() {
        var categories = Map.of(
                "SPATIAL_HOT_SPOT_VALUE_NOT_FINITE", ExecutionErrorCategory.SCHEMA,
                "SPATIAL_HOT_SPOT_CELL_LIMIT_EXCEEDED", ExecutionErrorCategory.CONFIGURATION);
        categories.forEach((code, category) -> {
            var error = new RunnerFailureClassifier().classify(
                    new SparkException("wrapper",
                            new IllegalArgumentException(code + " private-coordinate private-value")),
                    new RunnerFailureContext(
                            "node", "SPATIAL_HOT_SPOTS", "热点",
                            ExecutionFailurePhase.PROCESS, null));
            assertEquals(code, error.code());
            assertEquals(category, error.category());
            assertFalse(error.retryable());
            assertFalse(error.message().contains("private-"));
        });
    }

    @Test void multiVariableGridFailuresAreSafeAndNotRetryable() {
        var categories = Map.of(
                "SPATIAL_MULTI_VARIABLE_GRID_CELL_LIMIT_EXCEEDED", ExecutionErrorCategory.CONFIGURATION,
                "SPATIAL_MULTI_VARIABLE_GRID_VALUE_NOT_FINITE", ExecutionErrorCategory.SCHEMA,
                "SPATIAL_MULTI_VARIABLE_GRID_GEOMETRY_INVALID", ExecutionErrorCategory.SCHEMA);
        categories.forEach((code, category) -> {
            var error = new RunnerFailureClassifier().classify(
                    new SparkException("wrapper",
                            new IllegalArgumentException(code + " private-coordinate private-value")),
                    new RunnerFailureContext(
                            "node", "SPATIAL_MULTI_VARIABLE_GRID", "多变量格网",
                            ExecutionFailurePhase.PROCESS, null));
            assertEquals(code, error.code());
            assertEquals(category, error.category());
            assertFalse(error.retryable());
            assertFalse(error.message().contains("private-"));
        });
    }

    @Test void enrichFromGridNullIdentityIsAStableSchemaFailure() {
        var error = new RunnerFailureClassifier().classify(
                new SparkException("wrapper",
                        new IllegalArgumentException("SPATIAL_ENRICH_GRID_ID_NULL private-value")),
                new RunnerFailureContext(
                        "node", "SPATIAL_ENRICH_FROM_GRID", "格网丰富",
                        ExecutionFailurePhase.PROCESS, null));

        assertEquals("SPATIAL_ENRICH_GRID_ID_NULL", error.code());
        assertEquals(ExecutionErrorCategory.SCHEMA, error.category());
        assertFalse(error.retryable());
        assertFalse(error.message().contains("private-value"));
    }

    @Test void groupByProximityDataFailuresAreStableAndSafe() {
        for (String code : java.util.List.of(
                "SPATIAL_GROUP_GEOMETRY_INVALID",
                "SPATIAL_GROUP_TEMPORAL_INTERVAL_INVALID")) {
            var error = new RunnerFailureClassifier().classify(
                    new SparkException("wrapper",
                            new IllegalArgumentException(code + " private-coordinate private-time")),
                    new RunnerFailureContext(
                            "node", "SPATIAL_GROUP_BY_PROXIMITY", "邻近分组",
                            ExecutionFailurePhase.PROCESS, null));

            assertEquals(code, error.code());
            assertEquals(ExecutionErrorCategory.SCHEMA, error.category());
            assertFalse(error.retryable());
            assertFalse(error.message().contains("private-"));
        }
    }

    @Test void traceProximityDataFailuresAreStableAndSafe() {
        for (String code : java.util.List.of("TRACE_PROXIMITY_GEOMETRY_INVALID")) {
            var error = new RunnerFailureClassifier().classify(
                    new SparkException("wrapper",
                            new IllegalArgumentException(code + " private-coordinate private-id")),
                    new RunnerFailureContext(
                            "node", "TRACE_PROXIMITY_EVENTS", "邻近事件追踪",
                            ExecutionFailurePhase.PROCESS, null));

            assertEquals(code, error.code());
            assertEquals(ExecutionErrorCategory.SCHEMA, error.category());
            assertFalse(error.retryable());
            assertFalse(error.message().contains("private-"));
        }
    }

    @Test void snapTracksDataFailuresAreStableAndSafe() {
        var categories = Map.of(
                "SNAP_TRACKS_LINE_ID_INVALID", ExecutionErrorCategory.SCHEMA,
                "SNAP_TRACKS_LINE_ID_DUPLICATE", ExecutionErrorCategory.SCHEMA,
                "SNAP_TRACKS_NETWORK_NODE_INVALID", ExecutionErrorCategory.SCHEMA,
                "SNAP_TRACKS_CANDIDATE_COUNT_EXCEEDED", ExecutionErrorCategory.CONFIGURATION,
                "SNAP_TRACKS_POINT_GEOMETRY_INVALID", ExecutionErrorCategory.SCHEMA,
                "SNAP_TRACKS_LINE_GEOMETRY_INVALID", ExecutionErrorCategory.SCHEMA,
                "SNAP_TRACKS_MATCH_GEOMETRY_INVALID", ExecutionErrorCategory.SCHEMA,
                "SNAP_TRACKS_MATCH_STATE_INVALID", ExecutionErrorCategory.INTERNAL);
        categories.forEach((code, category) -> {
            var error = new RunnerFailureClassifier().classify(
                    new SparkException("wrapper",
                            new IllegalArgumentException(code + " private-coordinate private-id")),
                    new RunnerFailureContext(
                            "node", "SNAP_TRACKS", "吸附轨迹",
                            ExecutionFailurePhase.PROCESS, null));

            assertEquals(code, error.code());
            assertEquals(category, error.category());
            assertFalse(error.retryable());
            assertFalse(error.message().contains("private-"));
        });
    }

    @Test void hdbscanFailuresKeepTheirCauseCategoryAndNeverExposeObservations() {
        var categories = Map.of("SPATIAL_HDBSCAN_TREE_INVALID", ExecutionErrorCategory.INTERNAL,
                "SPATIAL_HDBSCAN_HIERARCHY_LIMIT_EXCEEDED", ExecutionErrorCategory.RESOURCE,
                "SPATIAL_HDBSCAN_NUMERIC_RANGE_INVALID", ExecutionErrorCategory.SCHEMA);
        categories.forEach((code, category) -> {
            var error = new RunnerFailureClassifier().classify(new SparkException("org.locationtech.jts wrapper",
                            new IllegalArgumentException(code + " private-point private-density")),
                    new RunnerFailureContext("node", "SPATIAL_POINT_CLUSTER", "聚类", ExecutionFailurePhase.PROCESS, null));
            assertEquals(code, error.code()); assertEquals(category, error.category());
            assertEquals(ExecutionFailurePhase.PROCESS, error.phase());
            assertFalse(error.retryable()); assertFalse(error.message().contains("private-"));
            assertNotNull(error.diagnosticId());
        });
    }

    @Test void dbscanIdentityPointAndPreviewErrorsAreSafeAndNotRetryable() {
        for (String code : java.util.List.of("SPATIAL_CLUSTER_FEATURE_ID_INVALID", "SPATIAL_CLUSTER_POINT_INVALID", "SPATIAL_CLUSTER_PREVIEW_NOT_EXECUTABLE")) {
            var error = new RunnerFailureClassifier().classify(new SparkException("wrapper", new IllegalArgumentException(code + " private-point")),
                    new RunnerFailureContext("node", "SPATIAL_POINT_CLUSTER", "聚类", ExecutionFailurePhase.PROCESS, null));
            assertEquals(code, error.code()); assertFalse(error.retryable()); assertFalse(error.message().contains("private-point"));
            assertEquals(code.endsWith("EXECUTABLE") ? ExecutionErrorCategory.CONFIGURATION : ExecutionErrorCategory.SCHEMA, error.category());
        }
    }

    @Test void connectedDissolveGeometryErrorsAreSafeAndNotRetryable() {
        var error = new RunnerFailureClassifier().classify(
                new SparkException("wrapper", new IllegalArgumentException(
                        "SPATIAL_DISSOLVE_GEOMETRY_INVALID private-coordinate")),
                new RunnerFailureContext(
                        "node", "SPATIAL_AGGREGATE", "Dissolve",
                        ExecutionFailurePhase.PROCESS, null));
        assertEquals("SPATIAL_DISSOLVE_GEOMETRY_INVALID", error.code());
        assertEquals(ExecutionErrorCategory.SCHEMA, error.category());
        assertFalse(error.retryable());
        assertFalse(error.message().contains("private-coordinate"));
    }
    @Test void gridFailuresAreSafeAndNotRetryable() {
        for (String code : java.util.List.of("SPATIAL_CALENDAR_WINDOW_LIMIT_EXCEEDED", "SPATIAL_CALENDAR_WINDOW_RANGE_INVALID", "SPATIAL_GRID_CELL_LIMIT_EXCEEDED", "SPATIAL_GRID_POINT_INVALID", "SPATIAL_H3_POINT_INVALID", "SPATIAL_H3_BOUNDARY_INVALID", "SPATIAL_H3_RUNTIME_UNAVAILABLE")) {
            var error = new RunnerFailureClassifier().classify(new SparkException("wrapper", new IllegalArgumentException(code + " private-coordinate")),
                    new RunnerFailureContext("node", "SPATIAL_BIN_AGGREGATE", "格网", ExecutionFailurePhase.PROCESS, null));
            assertEquals(code, error.code()); assertFalse(error.retryable()); assertFalse(error.message().contains("private-coordinate"));
            assertEquals(code.endsWith("UNAVAILABLE") || code.endsWith("LIMIT_EXCEEDED") ? ExecutionErrorCategory.CONFIGURATION : ExecutionErrorCategory.SCHEMA, error.category());
        }
    }
    @Test void centerNumericalAndCapacityFailuresAreSafeAndNotRetryable() {
        for (String code : java.util.List.of("SPATIAL_CENTER_GEOMETRY_INVALID", "SPATIAL_CENTER_WEIGHT_INVALID", "SPATIAL_CENTER_FEATURE_ID_INVALID",
                "SPATIAL_CENTER_NUMERIC_INVALID", "SPATIAL_CENTER_MEDIAN_NOT_CONVERGED", "SPATIAL_CENTER_GROUP_LIMIT_EXCEEDED")) {
            var error = new RunnerFailureClassifier().classify(new SparkException("org.locationtech.jts wrapper", new IllegalArgumentException(code + " private-data")),
                    new RunnerFailureContext("node", "SPATIAL_CENTER_DISPERSION", "中心", ExecutionFailurePhase.PROCESS, null));
            assertEquals(code, error.code()); assertFalse(error.retryable()); assertFalse(error.message().contains("private-data"));
            assertEquals(code.endsWith("LIMIT_EXCEEDED") ? ExecutionErrorCategory.CONFIGURATION : ExecutionErrorCategory.SCHEMA, error.category());
        }
    }
    private final RunnerFailureClassifier classifier = new RunnerFailureClassifier();
    private final RunnerFailureContext input = new RunnerFailureContext(
            "65b9615d-b72a-42c1-8e4e-f28a660da082", "JDBC_INPUT", "用户输入",
            ExecutionFailurePhase.READ, "dev_source.sys_user");

    @Test
    void mapsDwellGeometryErrorsToSafeNonRetryableSchemaFailures() {
        for (String code : new String[]{"TRACK_DWELL_POINT_INVALID", "TRACK_DWELL_CENTER_UNDEFINED", "TRACK_MOTION_POINT_INVALID"}) {
            var error = classifier.classify(new SparkException("wrapper",
                            new IllegalArgumentException(code)),
                    new RunnerFailureContext(input.nodeId(), code.startsWith("TRACK_MOTION") ? "TRACK_MOTION_STATISTICS" : "TRACK_FIND_DWELL", "轨迹分析", ExecutionFailurePhase.PROCESS, null));
            assertEquals(code, error.code());
            assertEquals(ExecutionErrorCategory.SCHEMA, error.category());
            assertFalse(error.retryable());
            assertTrue(error.message().contains(code.startsWith("TRACK_MOTION") ? "运动" : "驻留"));
        }
    }

    @Test
    void mapsWithinKeyErrorsWithoutExposingTheKey() {
        var error = classifier.classify(new SparkException("wrapper",
                new IllegalArgumentException("SPATIAL_WITHIN_AREA_KEY_INVALID")),
                new RunnerFailureContext(input.nodeId(), "SPATIAL_SUMMARIZE_WITHIN", "区域汇总", ExecutionFailurePhase.PROCESS, null));
        assertEquals("SPATIAL_WITHIN_AREA_KEY_INVALID", error.code());
        assertEquals(ExecutionErrorCategory.SCHEMA, error.category());
        assertFalse(error.retryable());
        assertTrue(error.message().contains("区域唯一键"));
    }

    @Test
    void mapsInvalidOverlayGeometryToSafeNonRetryableSchemaFailure() {
        var error = classifier.classify(new SparkException("wrapper",
                new IllegalArgumentException("SPATIAL_OVERLAY_INVALID_GEOMETRY")),
                new RunnerFailureContext(input.nodeId(), "SPATIAL_OVERLAY", "叠加", ExecutionFailurePhase.PROCESS, null));
        assertEquals("SPATIAL_OVERLAY_INVALID_GEOMETRY", error.code());
        assertEquals(ExecutionErrorCategory.SCHEMA, error.category());
        assertFalse(error.retryable());
        assertFalse(error.message().contains("POLYGON"));
    }

    @Test
    void mapsInvalidReconstructionGeometryWithoutCoordinatesOrExpressions() {
        var error = classifier.classify(new SparkException("wrapper", new IllegalArgumentException("TRACK_RECONSTRUCT_GEOMETRY_INVALID")),
                new RunnerFailureContext(input.nodeId(), "TRACK_RECONSTRUCT", "轨迹重建", ExecutionFailurePhase.PROCESS, null));
        assertEquals("TRACK_RECONSTRUCT_GEOMETRY_INVALID", error.code());
        assertEquals(ExecutionErrorCategory.SCHEMA, error.category());
        assertFalse(error.retryable());
        assertTrue(error.message().contains("轨迹重建"));
    }

    @Test void classifiesAreaGuardsWithoutRadiusOrGeometryValues() {
        for (String code : java.util.List.of("TRACK_BUFFER_DISTANCE_INVALID","TRACK_AREA_GEOMETRY_INVALID","TRACK_AREA_VERTEX_LIMIT_EXCEEDED",
                "TRACK_GEODESIC_BUFFER_RANGE_NOT_SUPPORTED", "TRACK_GEODESIC_HULL_RANGE_NOT_SUPPORTED",
                "TRACK_GEODESIC_HULL_WORK_LIMIT_EXCEEDED", "TRACK_GEODESIC_HULL_INVALID")) {
            var error = classifier.classify(new SparkException("wrapper",new IllegalArgumentException(code+" private-radius")),
                    new RunnerFailureContext(input.nodeId(),"TRACK_RECONSTRUCT","重建",ExecutionFailurePhase.PROCESS,null));
            assertEquals(code,error.code()); assertEquals(ExecutionErrorCategory.SCHEMA,error.category());
            assertFalse(error.retryable()); assertFalse(error.message().contains("private-radius"));
        }
    }

    @Test void classifiesDynamicBufferDistanceValuesWithoutExposingExpressions() {
        var error = classifier.classify(new SparkException("wrapper",
                        new IllegalArgumentException("GEOMETRY_BUFFER_DISTANCE_VALUE_INVALID private-expression")),
                new RunnerFailureContext(input.nodeId(), "GEOMETRY_BUFFER", "缓冲",
                        ExecutionFailurePhase.PROCESS, null));
        assertEquals("GEOMETRY_BUFFER_DISTANCE_VALUE_INVALID", error.code());
        assertEquals(ExecutionErrorCategory.SCHEMA, error.category());
        assertFalse(error.retryable());
        assertFalse(error.message().contains("private-expression"));
    }

    @Test void classifiesSharedGeodesicDistanceGuardsBeforeGenericSpatialFailures() {
        for (String node : java.util.List.of(
                "TRACK_RECONSTRUCT", "SPATIAL_NEAREST", "SPATIAL_JOIN")) {
            for (String code : java.util.List.of("GEODESIC_DISTANCE_COORDINATE_INVALID","GEODESIC_DISTANCE_GEOMETRY_UNSUPPORTED",
                    "GEODESIC_DISTANCE_GEOMETRY_INVALID","GEODESIC_DISTANCE_REGION_RANGE_NOT_SUPPORTED","GEODESIC_DISTANCE_WORK_LIMIT_EXCEEDED",
                    "GEODESIC_DISTANCE_PRECISION_NOT_REACHED","GEODESIC_TOPOLOGY_PRECISION_NOT_REACHED","INVALID_GEODESIC_DISTANCE_PRECISION","INVALID_GEODESIC_DISTANCE_WORK_LIMIT","INVALID_GEODESIC_DISTANCE_THRESHOLD")) {
                var error=classifier.classify(new SparkException("org.locationtech.jts wrapper",new IllegalArgumentException(code+" private-coordinate")),
                        new RunnerFailureContext(input.nodeId(),node,"测地距离",ExecutionFailurePhase.PROCESS,null));
                assertEquals(code,error.code()); assertFalse(error.retryable()); assertFalse(error.message().contains("private-coordinate"));
                assertEquals(code.startsWith("INVALID_") ? ExecutionErrorCategory.CONFIGURATION : ExecutionErrorCategory.SCHEMA,error.category());
            }
        }
    }

    @Test
    void classifiesSpatialJoinNearRuntimeGuardsWithoutLeakingValues() {
        for (String code : java.util.List.of(
                "SPATIAL_JOIN_GEOMETRY_INVALID",
                "SPATIAL_JOIN_DISTANCE_INVALID",
                "SPATIAL_JOIN_TEMPORAL_DIFFERENCE_INVALID")) {
            TaskExecutionError error = classifier.classify(
                    new SparkException("wrapper",
                            new IllegalArgumentException(code + " private-value")),
                    new RunnerFailureContext(
                            input.nodeId(), "SPATIAL_JOIN", "空间 Near",
                            ExecutionFailurePhase.PROCESS, null));

            assertEquals(code, error.code());
            assertEquals(ExecutionErrorCategory.SCHEMA, error.category());
            assertFalse(error.retryable());
            assertFalse(error.message().contains("private-value"));
        }
    }

    @Test
    void classifiesGeodesicGuardsWithoutLeakingCoordinates() {
        for (String code : java.util.List.of("TRACK_GEODESIC_COORDINATE_INVALID", "TRACK_GEODESIC_VERTEX_LIMIT_EXCEEDED", "INVALID_TRACK_GEODESIC_SEGMENT_LENGTH")) {
            var error = classifier.classify(new SparkException("wrapper", new IllegalArgumentException(code + " private-coordinate")),
                    new RunnerFailureContext(input.nodeId(), "TRACK_RECONSTRUCT", "重建", ExecutionFailurePhase.PROCESS, null));
            assertEquals(code, error.code());
            assertEquals(code.equals("TRACK_GEODESIC_COORDINATE_INVALID") ? ExecutionErrorCategory.SCHEMA : ExecutionErrorCategory.CONFIGURATION, error.category());
            assertFalse(error.retryable());
            assertFalse(error.message().contains("private-coordinate"));
        }
    }

    @Test
    void classifiesUnaryMarkersBeforeGenericGeometryFailures() {
        for (String code : java.util.List.of("GEOMETRY_UNARY_INPUT_INVALID", "GEOMETRY_UNARY_KIND_UNSUPPORTED", "GEOMETRY_UNARY_DIMENSION_UNSUPPORTED", "GEOMETRY_SIMPLIFY_RESULT_INVALID")) {
            var error = classifier.classify(new SparkException("org.locationtech.jts wrapper", new IllegalArgumentException(code + " private-coordinate")),
                    new RunnerFailureContext(input.nodeId(), "GEOMETRY_SIMPLIFY", "简化", ExecutionFailurePhase.PROCESS, null));
            assertEquals(code, error.code()); assertEquals(ExecutionErrorCategory.SCHEMA, error.category());
            assertFalse(error.retryable()); assertFalse(error.message().contains("private-coordinate"));
        }
    }

    @Test
    void classifiesNearestGuardsBeforeLibraryFallbackWithoutValues() {
        for (String code : java.util.List.of("SPATIAL_NEAREST_GEOMETRY_INVALID", "GEODESIC_NEAREST_REQUIRES_POINTS", "SPATIAL_NEAREST_SOURCE_ID_INVALID",
                "SPATIAL_NEAREST_CANDIDATE_ID_INVALID", "SPATIAL_NEAREST_DISTANCE_INVALID", "SPATIAL_NEAREST_CONNECTION_VERTEX_LIMIT_EXCEEDED")) {
            var error = classifier.classify(new SparkException("org.locationtech.jts wrapper", new IllegalArgumentException(code + " private-coordinate")),
                    new RunnerFailureContext(input.nodeId(), "SPATIAL_NEAREST", "最近邻", ExecutionFailurePhase.PROCESS, null));
            assertEquals(code, error.code());
            assertEquals(code.endsWith("VERTEX_LIMIT_EXCEEDED") ? ExecutionErrorCategory.CONFIGURATION : ExecutionErrorCategory.SCHEMA, error.category());
            assertFalse(error.retryable()); assertFalse(error.message().contains("private-coordinate"));
        }
    }

    @Test
    void findsPermissionSqlStateInsideSparkWrapper() {
        TaskExecutionError error = classifier.classify(
                new SparkException("Spark JDBC failed", new SQLException("permission denied", "42501")), input);

        assertEquals("JDBC_PERMISSION_DENIED", error.code());
        assertEquals(ExecutionErrorCategory.PERMISSION, error.category());
        assertEquals("42501", error.sqlState());
        assertEquals("数据源用户无权读取表 dev_source.sys_user", error.message());
        assertFalse(error.retryable());
        assertNotNull(error.diagnosticId());
    }

    @Test
    void reportsAmbiguousTrackOrderWithoutExposingTheObservation() {
        TaskExecutionError error = classifier.classify(new SparkException("wrapped",
                        new IllegalArgumentException("TRACK_OBSERVATION_ORDER_NOT_UNIQUE")),
                new RunnerFailureContext(input.nodeId(), "TRACK_DETECT_INCIDENTS", "事件",
                        ExecutionFailurePhase.PROCESS, null));
        assertEquals("TRACK_OBSERVATION_ORDER_NOT_UNIQUE", error.code());
        assertEquals(ExecutionErrorCategory.SCHEMA, error.category());
        assertFalse(error.retryable());
        assertTrue(error.message().contains("同时间"));
    }

    @Test
    void reportsAmbiguousSpatialJoinKeepOrderAsSafeSchemaFailure() {
        TaskExecutionError error = classifier.classify(new SparkException("wrapped",
                        new IllegalArgumentException(
                                "SPATIAL_JOIN_KEEP_ORDER_NOT_UNIQUE private-record")),
                new RunnerFailureContext(
                        input.nodeId(), "SPATIAL_JOIN", "一对一空间连接",
                        ExecutionFailurePhase.PROCESS, null));

        assertEquals("SPATIAL_JOIN_KEEP_ORDER_NOT_UNIQUE", error.code());
        assertEquals(ExecutionErrorCategory.SCHEMA, error.category());
        assertFalse(error.retryable());
        assertTrue(error.message().contains("稳定排序"));
        assertFalse(error.message().contains("private-record"));
    }

    @Test
    void mapsStableJdbcCategoriesAndRetryability() {
        assertClassified(new SQLException("auth", "28000"), "JDBC_AUTHENTICATION_FAILED",
                ExecutionErrorCategory.AUTHENTICATION, false);
        assertClassified(new SQLException("connect", "08006"), "JDBC_CONNECTION_FAILED",
                ExecutionErrorCategory.CONNECTION, true);
        assertClassified(new SQLException("constraint", "23505"), "JDBC_CONSTRAINT_VIOLATION",
                ExecutionErrorCategory.CONSTRAINT, false);
        assertClassified(new SQLTimeoutException("timeout"), "JDBC_TIMEOUT",
                ExecutionErrorCategory.TIMEOUT, true);
        assertClassified(new RuntimeException(new SocketTimeoutException("read timeout")), "JDBC_TIMEOUT",
                ExecutionErrorCategory.TIMEOUT, true);
    }

    @Test
    void classifiesConfigurationAndSpatialExecutionPrerequisites() {
        TaskExecutionError staleQuery = classifier.classify(
                new RunnerExecutionException("JDBC_QUERY_SCHEMA_STALE", "changed", input.nodeId()), input);
        assertEquals("JDBC_QUERY_SCHEMA_STALE", staleQuery.code());
        assertEquals(ExecutionErrorCategory.CONFIGURATION, staleQuery.category());
        assertNull(staleQuery.sqlState());

        TaskExecutionError spatialMetadata = classifier.classify(
                new RunnerExecutionException(
                        "SPATIAL_TARGET_METADATA_UNAVAILABLE",
                        "secret physical detail",
                        input.nodeId()
                ),
                input
        );
        assertEquals("SPATIAL_TARGET_METADATA_UNAVAILABLE", spatialMetadata.code());
        assertEquals(ExecutionErrorCategory.SCHEMA, spatialMetadata.category());
        assertEquals("目标 Geometry 字段缺少写入所需元数据", spatialMetadata.message());

        for (String code : new String[]{
                "OVERWRITE_DATABASE_NOT_SUPPORTED",
                "UPSERT_DATABASE_NOT_SUPPORTED",
                "SPATIAL_JDBC_UNSUPPORTED"
        }) {
            TaskExecutionError unsupported = classifier.classify(
                    new RunnerExecutionException(code, "unsafe detail", input.nodeId()),
                    input
            );
            assertEquals(code, unsupported.code());
            assertEquals(ExecutionErrorCategory.CONFIGURATION, unsupported.category());
            assertFalse(unsupported.message().contains("unsafe detail"));
        }

        RunnerFailureContext join = new RunnerFailureContext(
                "e55c50d7-374d-4fe0-aede-e1648988af23", "JOIN", "订单客户 Join",
                ExecutionFailurePhase.PROCESS, "order_customer");
        TaskExecutionError processor = classifier.classify(new IllegalStateException("boom"), join);
        assertEquals("PROCESSOR_EXECUTION_FAILED", processor.code());
        assertEquals(ExecutionErrorCategory.INTERNAL, processor.category());

        RunnerFailureContext rename = new RunnerFailureContext(
                "8c9173e0-47e7-4870-8a37-9e41b9e39be7", "RENAME", "订单重命名",
                ExecutionFailurePhase.PROCESS, "source_orders");
        TaskExecutionError renameProcessor = classifier.classify(new IllegalStateException("boom"), rename);
        assertEquals("PROCESSOR_EXECUTION_FAILED", renameProcessor.code());
        assertEquals(ExecutionErrorCategory.INTERNAL, renameProcessor.category());
    }

    @Test
    void classifiesAdvancedProcessorsAndValueMappingConstraintWithoutLeakingValues() {
        for (String nodeType : new String[]{
                "NULL_HANDLING", "VALUE_MAPPING", "MASK_FIELDS", "JSON_EXTRACT",
                "WINDOW", "TOP_N"
        }) {
            RunnerFailureContext processor = new RunnerFailureContext(
                    "63e9e935-91ed-49a8-a571-37518d9a76e7",
                    nodeType,
                    "处理器",
                    ExecutionFailurePhase.PROCESS,
                    "processed_orders"
            );
            TaskExecutionError fallback = classifier.classify(
                    new IllegalStateException("boom"),
                    processor
            );
            assertEquals("PROCESSOR_EXECUTION_FAILED", fallback.code());
            assertEquals(ExecutionErrorCategory.INTERNAL, fallback.category());
        }

        RunnerFailureContext valueMapping = new RunnerFailureContext(
                "4dd824bc-e921-47c0-a9b4-8d2e156bc6d3",
                "VALUE_MAPPING",
                "状态映射",
                ExecutionFailurePhase.PROCESS,
                "mapped_orders"
        );
        TaskExecutionError unmatched = classifier.classify(
                new SparkException(
                        "job failed",
                        new IllegalStateException(
                                "VALUE_MAPPING_UNMATCHED_VALUE column=status secret-value")
                ),
                valueMapping
        );

        assertEquals("VALUE_MAPPING_UNMATCHED_VALUE", unmatched.code());
        assertEquals(ExecutionErrorCategory.CONSTRAINT, unmatched.category());
        assertEquals("值映射遇到未配置的非 NULL 值", unmatched.message());
        assertFalse(unmatched.retryable());
        assertFalse(unmatched.message().contains("secret-value"));
    }

    @Test
    void preservesGeometryConstructCodesWhenLazyFailureSurfacesAtOutput() {
        RunnerFailureContext output = new RunnerFailureContext(
                "b797597b-eb3e-428c-aaf5-186fa8d7fc28",
                "JDBC_OUTPUT",
                "空间结果输出",
                ExecutionFailurePhase.WRITE,
                "geometry_result"
        );

        TaskExecutionError parse = classifier.classify(
                new SparkException(
                        "write failed",
                        new ParseException("secret malformed geometry")
                ),
                output
        );
        TaskExecutionError kind = classifier.classify(
                new SparkException(
                        "write failed",
                        new IllegalStateException("GEOMETRY_CONSTRUCT_KIND_MISMATCH")
                ),
                output
        );

        assertEquals("GEOMETRY_CONSTRUCT_PARSE_FAILED", parse.code());
        assertEquals(ExecutionErrorCategory.SCHEMA, parse.category());
        assertEquals("Geometry 来源内容解析失败", parse.message());
        assertFalse(parse.message().contains("secret"));
        assertEquals("GEOMETRY_CONSTRUCT_KIND_MISMATCH", kind.code());
        assertEquals(ExecutionErrorCategory.SCHEMA, kind.category());
        assertEquals("Geometry 实际类型与目标类型不一致", kind.message());
    }

    @Test
    void classifiesSpatialEnrichmentFailuresWithoutExposingGeometryValues() {
        String[] nodeTypes = {
                "GEOMETRY_REPAIR", "GEOMETRY_BUFFER", "GEOMETRY_EXPLODE",
                "SPATIAL_CLIP", "SPATIAL_AGGREGATE"
        };
        String[] expectedCodes = {
                "GEOMETRY_REPAIR_FAILED", "GEOMETRY_BUFFER_FAILED", "GEOMETRY_EXPLODE_FAILED",
                "SPATIAL_CLIP_FAILED", "SPATIAL_AGGREGATE_FAILED"
        };
        String[] expectedMessages = {
                "Geometry 修复失败", "Geometry Buffer 计算失败", "Geometry 拆分失败",
                "空间裁剪失败", "空间聚合失败"
        };

        for (int index = 0; index < nodeTypes.length; index++) {
            RunnerFailureContext context = new RunnerFailureContext(
                    "55f4be36-adb6-4f59-ad10-0fb9b62b7a83",
                    nodeTypes[index],
                    "空间处理",
                    ExecutionFailurePhase.PROCESS,
                    "spatial_result"
            );
            TaskExecutionError error = classifier.classify(
                    new SparkException(
                            "wrapped",
                            new TopologyException("POINT (secret-coordinate)")
                    ),
                    context
            );

            assertEquals(expectedCodes[index], error.code());
            assertEquals(ExecutionErrorCategory.SCHEMA, error.category());
            assertEquals(expectedMessages[index], error.message());
            assertFalse(error.message().contains("secret-coordinate"));
        }
    }

    @Test
    void fallsBackToModelNodeErrorsWithoutHidingJdbcSqlState() {
        RunnerFailureContext modelInput = new RunnerFailureContext(
                "9d7ae286-591d-4eca-8eff-c002a37ce5d2", "MODEL_INPUT", "订单模型输入",
                ExecutionFailurePhase.READ, "model_schema.orders");
        RunnerFailureContext modelOutput = new RunnerFailureContext(
                "b797597b-eb3e-428c-aaf5-186fa8d7fc28", "MODEL_OUTPUT", "订单模型输出",
                ExecutionFailurePhase.WRITE, "model_schema.dwd_orders");

        TaskExecutionError inputFallback = classifier.classify(new IllegalStateException("boom"), modelInput);
        TaskExecutionError outputFallback = classifier.classify(new IllegalStateException("boom"), modelOutput);
        TaskExecutionError permission = classifier.classify(
                new SQLException("permission denied", "42501"), modelOutput);

        assertEquals("MODEL_INPUT_FAILED", inputFallback.code());
        assertEquals("模型输入节点执行失败", inputFallback.message());
        assertEquals(ExecutionErrorCategory.EXTERNAL_SYSTEM, inputFallback.category());
        assertEquals("MODEL_OUTPUT_FAILED", outputFallback.code());
        assertEquals("模型输出节点执行失败", outputFallback.message());
        assertEquals(ExecutionErrorCategory.EXTERNAL_SYSTEM, outputFallback.category());
        assertEquals("JDBC_PERMISSION_DENIED", permission.code());
        assertEquals("42501", permission.sqlState());
    }

    @Test
    void preservesSnapshotSyncFailuresAndUsesTheModelSpecificFallback() {
        RunnerFailureContext jdbcSnapshot = new RunnerFailureContext(
                "3b645b40-1de7-4afb-a276-80a31772a5a9",
                "JDBC_SNAPSHOT_SYNC_OUTPUT",
                "JDBC 快照同步",
                ExecutionFailurePhase.WRITE,
                "public.reservoir"
        );
        RunnerFailureContext modelSnapshot = new RunnerFailureContext(
                "ac801c40-d13a-4f0d-9c98-b3d5c7ef7d37",
                "MODEL_SNAPSHOT_SYNC_OUTPUT",
                "模型快照同步",
                ExecutionFailurePhase.WRITE,
                "public.district"
        );

        TaskExecutionError jdbcFallback = classifier.classify(
                new IllegalStateException("boom"), jdbcSnapshot);
        TaskExecutionError modelFallback = classifier.classify(
                new RunnerExecutionException(
                        "MODEL_SNAPSHOT_SYNC_OUTPUT_FAILED",
                        "private target details",
                        modelSnapshot.nodeId(),
                        new SQLException("private SQL", "HY000")
                ),
                modelSnapshot
        );
        TaskExecutionError constraint = classifier.classify(
                new RunnerExecutionException(
                        "SNAPSHOT_SYNC_EMPTY_SOURCE_DELETE_BLOCKED",
                        "private key details",
                        modelSnapshot.nodeId()
                ),
                modelSnapshot
        );
        TaskExecutionError permission = classifier.classify(
                new RunnerExecutionException(
                        "SNAPSHOT_SYNC_OUTPUT_FAILED",
                        "private target details",
                        jdbcSnapshot.nodeId(),
                        new SQLException("permission denied", "42501")
                ),
                jdbcSnapshot
        );

        assertEquals("SNAPSHOT_SYNC_OUTPUT_FAILED", jdbcFallback.code());
        assertEquals("JDBC 快照同步失败", jdbcFallback.message());
        assertEquals("MODEL_SNAPSHOT_SYNC_OUTPUT_FAILED", modelFallback.code());
        assertEquals("模型快照同步失败", modelFallback.message());
        assertEquals(ExecutionErrorCategory.EXTERNAL_SYSTEM, modelFallback.category());
        assertEquals("HY000", modelFallback.sqlState());
        assertEquals("SNAPSHOT_SYNC_EMPTY_SOURCE_DELETE_BLOCKED", constraint.code());
        assertEquals(ExecutionErrorCategory.CONSTRAINT, constraint.category());
        assertFalse(constraint.message().contains("private"));
        assertEquals("JDBC_PERMISSION_DENIED", permission.code());
        assertEquals(ExecutionErrorCategory.PERMISSION, permission.category());
        assertEquals("42501", permission.sqlState());
    }

    @Test
    void classifiesFileOutputFailuresWithoutReturningS3Credentials() {
        RunnerFailureContext output = new RunnerFailureContext(
                "b797597b-eb3e-428c-aaf5-186fa8d7fc28", "FILE_OUTPUT", "S3 文件输出",
                ExecutionFailurePhase.WRITE, "s3a://exports/orders");

        TaskExecutionError exists = classifier.classify(
                new FileAlreadyExistsException("s3a://exports/orders"), output);
        TaskExecutionError denied = classifier.classify(
                new IllegalStateException("AccessDenied secretKey=should-not-leak"), output);
        TaskExecutionError invalidGeometry = classifier.classify(
                new RunnerExecutionException(
                        "SHAPEFILE_GEOMETRY_TYPE_MISMATCH",
                        "row=2 geometry=POINT (120 30)",
                        output.nodeId()),
                output);
        TaskExecutionError sizeLimit = classifier.classify(
                new RunnerExecutionException(
                        "SHAPEFILE_SIZE_LIMIT_EXCEEDED", "local=/tmp/private", output.nodeId()),
                output);
        TaskExecutionError upload = classifier.classify(
                new RunnerExecutionException(
                        "SHAPEFILE_UPLOAD_FAILED", "secretKey=should-not-leak", output.nodeId()),
                output);
        TaskExecutionError wrappedExists = classifier.classify(
                new RunnerExecutionException(
                        "FILE_OUTPUT_TARGET_EXISTS", "private target", output.nodeId()),
                output);
        TaskExecutionError wrappedDenied = classifier.classify(
                new RunnerExecutionException(
                        "SHAPEFILE_UPLOAD_FAILED", "upload failed", output.nodeId(),
                        new IOException("AccessDenied secretKey=should-not-leak")),
                output);
        TaskExecutionError wrappedConnection = classifier.classify(
                new RunnerExecutionException(
                        "SHAPEFILE_UPLOAD_FAILED", "upload failed", output.nodeId(),
                        new ConnectException("private endpoint")),
                output);
        TaskExecutionError geoParquetDenied = classifier.classify(
                new RunnerExecutionException(
                        "GEOPARQUET_WRITE_FAILED", "write failed", output.nodeId(),
                        new IOException("AccessDenied secretKey=should-not-leak")),
                output);
        TaskExecutionError geoParquetConnection = classifier.classify(
                new RunnerExecutionException(
                        "GEOPARQUET_WRITE_FAILED", "write failed", output.nodeId(),
                        new ConnectException("private endpoint")),
                output);
        TaskExecutionError geoParquetFormatFailure = classifier.classify(
                new RunnerExecutionException(
                        "GEOPARQUET_WRITE_FAILED", "geometry=POINT (secret-coordinate)",
                        output.nodeId()),
                output);
        TaskExecutionError geoParquetWrappedIo = classifier.classify(
                new RunnerExecutionException(
                        "GEOPARQUET_WRITE_FAILED", "path=/private/output", output.nodeId(),
                        new IOException("connection reset for secret object")),
                output);
        TaskExecutionError socketTimeout = classifier.classify(
                new RuntimeException(
                        "s3a://private-bucket/secret-object",
                        new SocketTimeoutException("read timeout for secret object")),
                output);

        assertEquals("FILE_OUTPUT_TARGET_EXISTS", exists.code());
        assertEquals(ExecutionErrorCategory.CONSTRAINT, exists.category());
        assertEquals("FILE_OUTPUT_PERMISSION_DENIED", denied.code());
        assertEquals(ExecutionErrorCategory.PERMISSION, denied.category());
        assertFalse(denied.message().contains("should-not-leak"));
        assertEquals(ExecutionErrorCategory.SCHEMA, invalidGeometry.category());
        assertEquals("Shapefile Geometry 与目标 Shape 类型不一致", invalidGeometry.message());
        assertFalse(invalidGeometry.message().contains("POINT"));
        assertEquals(ExecutionErrorCategory.RESOURCE, sizeLimit.category());
        assertFalse(sizeLimit.retryable());
        assertEquals(ExecutionErrorCategory.EXTERNAL_SYSTEM, upload.category());
        assertEquals("Shapefile S3 制品提交失败", upload.message());
        assertFalse(upload.message().contains("should-not-leak"));
        assertEquals("FILE_OUTPUT_TARGET_EXISTS", wrappedExists.code());
        assertEquals(ExecutionErrorCategory.CONSTRAINT, wrappedExists.category());
        assertEquals("FILE_OUTPUT_PERMISSION_DENIED", wrappedDenied.code());
        assertEquals(ExecutionErrorCategory.PERMISSION, wrappedDenied.category());
        assertFalse(wrappedDenied.message().contains("should-not-leak"));
        assertEquals("FILE_OUTPUT_CONNECTION_FAILED", wrappedConnection.code());
        assertEquals(ExecutionErrorCategory.CONNECTION, wrappedConnection.category());
        assertTrue(wrappedConnection.retryable());
        assertEquals("FILE_OUTPUT_PERMISSION_DENIED", geoParquetDenied.code());
        assertEquals(ExecutionErrorCategory.PERMISSION, geoParquetDenied.category());
        assertFalse(geoParquetDenied.message().contains("should-not-leak"));
        assertEquals("FILE_OUTPUT_CONNECTION_FAILED", geoParquetConnection.code());
        assertEquals(ExecutionErrorCategory.CONNECTION, geoParquetConnection.category());
        assertTrue(geoParquetConnection.retryable());
        assertEquals("GEOPARQUET_WRITE_FAILED", geoParquetFormatFailure.code());
        assertEquals(ExecutionErrorCategory.EXTERNAL_SYSTEM, geoParquetFormatFailure.category());
        assertFalse(geoParquetFormatFailure.retryable());
        assertFalse(geoParquetFormatFailure.message().contains("secret-coordinate"));
        assertEquals("FILE_OUTPUT_CONNECTION_FAILED", geoParquetWrappedIo.code());
        assertEquals(ExecutionErrorCategory.CONNECTION, geoParquetWrappedIo.category());
        assertTrue(geoParquetWrappedIo.retryable());
        assertFalse(geoParquetWrappedIo.message().contains("secret object"));
        assertEquals("FILE_OUTPUT_CONNECTION_FAILED", socketTimeout.code());
        assertEquals(ExecutionErrorCategory.CONNECTION, socketTimeout.category());
        assertTrue(socketTimeout.retryable());
        assertFalse(socketTimeout.message().contains("private-bucket"));
    }

    @Test
    void classifiesHttpApiFailuresWithReadPhaseAndSafeStableCodes() {
        RunnerFailureContext httpInput = new RunnerFailureContext(
                "c99104f5-05cb-413a-999c-dcf35cce51b8", "HTTP_API_INPUT", "订单 API 输入",
                ExecutionFailurePhase.READ, "8cf73463-d1b5-4327-bce3-f8e94b965be1");

        TaskExecutionError authentication = classifier.classify(
                new RunnerExecutionException(
                        "API_AUTHENTICATION_FAILED", "token=should-not-be-returned", httpInput.nodeId()),
                httpInput);
        TaskExecutionError fallback = classifier.classify(new IllegalStateException("remote failure"), httpInput);
        TaskExecutionError staging = classifier.classify(
                new RunnerExecutionException(
                        "API_BATCH_STAGING_FAILED", "spark-local-dir=/sensitive/path", httpInput.nodeId()),
                httpInput);

        assertEquals("API_AUTHENTICATION_FAILED", authentication.code());
        assertEquals("HTTP API 认证失败", authentication.message());
        assertEquals(ExecutionErrorCategory.AUTHENTICATION, authentication.category());
        assertEquals(ExecutionFailurePhase.READ, authentication.phase());
        assertFalse(authentication.message().contains("should-not-be-returned"));
        assertNotNull(authentication.diagnosticId());
        assertEquals("HTTP_API_INPUT_FAILED", fallback.code());
        assertEquals("HTTP API 输入节点执行失败", fallback.message());
        assertEquals(ExecutionErrorCategory.EXTERNAL_SYSTEM, fallback.category());
        assertEquals("API_BATCH_STAGING_FAILED", staging.code());
        assertEquals("HTTP API 分批暂存失败", staging.message());
        assertEquals(ExecutionErrorCategory.RESOURCE, staging.category());
        assertFalse(staging.retryable());
        assertFalse(staging.message().contains("sensitive"));
    }

    @Test
    void preservesFileInputIdentityWhenLazyReadFailsDuringOutputAction() {
        String fileNodeId = "80efb303-fd40-47f3-9a18-a41e746d7be3";
        RunnerFailureContext output = new RunnerFailureContext(
                "cc55b3c8-dd26-4f19-be66-61cd5b49b8cd",
                "JDBC_OUTPUT",
                "订单输出",
                ExecutionFailurePhase.WRITE,
                "orders_target"
        );
        FileDatasetReadException readFailure = new FileDatasetReadException(
                "FILE_DATASET_PARSE_FAILED",
                "文件数据集内容解析失败",
                fileNodeId,
                "订单文件输入",
                false,
                new IllegalArgumentException("invalid parquet")
        );

        TaskExecutionError error = classifier.classify(new SparkException("write failed", readFailure), output);

        assertEquals("FILE_DATASET_PARSE_FAILED", error.code());
        assertEquals(ExecutionErrorCategory.SCHEMA, error.category());
        assertEquals(ExecutionFailurePhase.READ, error.phase());
        assertEquals(fileNodeId, error.nodeId());
        assertEquals("FILE_DATASET_INPUT", error.nodeType());
        assertEquals("订单文件输入", error.nodeName());
        assertFalse(error.retryable());
    }

    @Test
    void preservesFileParseFailureAndInputIdentityDuringLazyRead() {
        String fileNodeId = "2ca10a7e-2030-4926-ad79-a9324c45c44c";
        FileDatasetReadException readFailure = new FileDatasetReadException(
                "FILE_DATASET_PARSE_FAILED",
                "文件数据集内容解析失败",
                fileNodeId,
                "行政区文件输入",
                false,
                new IllegalArgumentException("secret object path")
        );

        TaskExecutionError error = classifier.classify(
                new SparkException("write failed", readFailure),
                new RunnerFailureContext(
                        "784954f2-e203-4f28-b9f5-c05c551a7d36",
                        "JDBC_OUTPUT",
                        "空间结果输出",
                        ExecutionFailurePhase.WRITE,
                        "region_result"
                )
        );

        assertEquals("FILE_DATASET_PARSE_FAILED", error.code());
        assertEquals(ExecutionErrorCategory.SCHEMA, error.category());
        assertEquals(ExecutionFailurePhase.READ, error.phase());
        assertEquals(fileNodeId, error.nodeId());
        assertEquals("FILE_DATASET_INPUT", error.nodeType());
        assertEquals("行政区文件输入", error.nodeName());
        assertEquals("文件数据集内容解析失败", error.message());
        assertFalse(error.message().contains("secret"));
    }

    @Test
    void classifiesUnresolvedSparkColumnsWithoutLeakingSparkPlanOrLiterals() {
        TaskExecutionError error = classifier.classify(new SparkException(
                "SELECT secret_literal FROM sensitive_table",
                analysisException(
                        "UNRESOLVED_COLUMN.WITH_SUGGESTION",
                        Map.of(
                                "objectName", "`dept_name`",
                                "proposal", "`dept_id`, `user_name`, `sex`, `created_at`, `status`, `ignored`"
                        )
                )
        ), RunnerFailureContext.task(ExecutionFailurePhase.PROCESS));

        assertEquals("SPARK_UNRESOLVED_COLUMN", error.code());
        assertEquals(ExecutionErrorCategory.SCHEMA, error.category());
        assertFalse(error.retryable());
        assertEquals("Spark 代码引用的字段 dept_name 不存在。可用字段建议：dept_id、user_name、sex、created_at、status。", error.message());
        assertFalse(error.message().contains("secret_literal"));
        assertFalse(error.message().contains("sensitive_table"));
    }

    @Test
    void classifiesOtherUserCatalystAnalysisFailuresAsSchemaErrors() {
        TaskExecutionError ambiguous = classifier.classify(
                analysisException(
                        "AMBIGUOUS_REFERENCE",
                        Map.of("name", "`id`", "referenceNames", "[`left`.`id`, `right`.`id`]")),
                input);
        TaskExecutionError dataType = classifier.classify(
                analysisException(
                        "DATATYPE_MISMATCH.BINARY_OP_DIFF_TYPES",
                        Map.of("sqlExpr", "`a` + `b`", "left", "STRING", "right", "INT")),
                input);
        TaskExecutionError generic = classifier.classify(
                analysisException("UNSUPPORTED_FEATURE.ANALYZE_VIEW", Map.of()), input);

        assertEquals("SPARK_AMBIGUOUS_REFERENCE", ambiguous.code());
        assertEquals(ExecutionErrorCategory.SCHEMA, ambiguous.category());
        assertEquals("Spark 代码引用的字段存在歧义，请使用 Dataset alias 限定来源", ambiguous.message());
        assertEquals("SPARK_DATATYPE_MISMATCH", dataType.code());
        assertEquals(ExecutionErrorCategory.SCHEMA, dataType.category());
        assertEquals("SPARK_ANALYSIS_FAILED", generic.code());
        assertEquals(ExecutionErrorCategory.SCHEMA, generic.category());
    }

    private static AnalysisException analysisException(String condition, Map<String, String> parameters) {
        return new AnalysisException(
                condition,
                scala.collection.immutable.Map$.MODULE$.from(
                        scala.jdk.javaapi.CollectionConverters.asScala(parameters)),
                scala.Option.empty());
    }

    @Test void classifiesGeneratedGridGeometryFailureWithoutExposingCoordinates() {
        var error = classifier.classify(new SparkException("POLYGON secret_coordinate",new IllegalArgumentException("SPATIAL_GRID_GEOMETRY_INVALID")),input);
        assertEquals("SPATIAL_GRID_GEOMETRY_INVALID",error.code());
        assertEquals(ExecutionErrorCategory.SCHEMA,error.category()); assertFalse(error.retryable());
        assertFalse(error.message().contains("secret_coordinate"));
    }

    private void assertClassified(
            Throwable throwable,
            String code,
            ExecutionErrorCategory category,
            boolean retryable
    ) {
        TaskExecutionError error = classifier.classify(throwable, input);
        assertEquals(code, error.code());
        assertEquals(category, error.category());
        assertEquals(retryable, error.retryable());
    }
}
