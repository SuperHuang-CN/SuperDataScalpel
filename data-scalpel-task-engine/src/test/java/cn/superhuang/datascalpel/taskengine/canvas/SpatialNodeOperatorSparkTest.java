package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout;
import cn.superhuang.data.scalpel.contract.task.CanvasTableOrigin;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.GeometryConstructConfiguration;
import cn.superhuang.data.scalpel.contract.task.GeometryConstructNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.GeometryConstructSource;
import cn.superhuang.data.scalpel.contract.task.GeometryBufferConfiguration;
import cn.superhuang.data.scalpel.contract.task.GeometryBufferNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.GeometryExplodeConfiguration;
import cn.superhuang.data.scalpel.contract.task.GeometryExplodeNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.GeometryRepairConfiguration;
import cn.superhuang.data.scalpel.contract.task.GeometryRepairNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.GeometrySerializationFormat;
import cn.superhuang.data.scalpel.contract.task.GeometrySerializeConfiguration;
import cn.superhuang.data.scalpel.contract.task.GeometrySerializeNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.GeometryValidateConfiguration;
import cn.superhuang.data.scalpel.contract.task.GeometryValidateNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JoinType;
import cn.superhuang.data.scalpel.contract.task.MetadataSnapshot;
import cn.superhuang.data.scalpel.contract.task.SpatialJoinCondition;
import cn.superhuang.data.scalpel.contract.task.SpatialJoinConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialJoinNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialClipConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialClipNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialAggregateConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialAggregateNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialAggregation;
import cn.superhuang.data.scalpel.contract.task.SpatialAggregationKind;
import cn.superhuang.data.scalpel.contract.task.SpatialMeasureConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialMeasureMode;
import cn.superhuang.data.scalpel.contract.task.SpatialMeasureNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialMeasurement;
import cn.superhuang.data.scalpel.contract.task.SpatialPredicate;
import cn.superhuang.data.scalpel.contract.task.SpatialTransformConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialTransformNodeDefinition;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.datascalpel.taskengine.spark.SedonaSparkSupport;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.WKBWriter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SpatialNodeOperatorSparkTest {
    private static final CanvasNodeLayout LAYOUT = new CanvasNodeLayout(0d, 0d, 240d, 120d);
    private SparkSession spark;

    @BeforeAll
    void startSpark() {
        spark = SedonaSparkSupport.initialize(SedonaSparkSupport.builder()
                .master("local[1]")
                .appName("spatial-node-operator-test")
                .config("spark.ui.enabled", "false")
                .config("spark.driver.host", "127.0.0.1")
                .config("spark.driver.bindAddress", "127.0.0.1")
                .config("spark.sql.caseSensitive", "true")
                .getOrCreate());
    }

    @AfterAll
    void stopSpark() {
        if (spark != null) {
            spark.stop();
        }
    }

    @Test
    void transformsGeometryCrsAndPreservesOtherColumns() {
        SparkCanvasTable source = table(
                "orders",
                List.of(
                        scalar("order_id"),
                        geometry("location", GeometryKind.POINT, 4326)
                )
        );
        RecordingIssueSink issues = new RecordingIssueSink();

        CanvasNodeOperationResult result = new SpatialTransformNodeOperator().apply(
                new SpatialTransformNodeDefinition(
                        UUID.randomUUID().toString(),
                        "转换坐标系",
                        LAYOUT,
                        new SpatialTransformConfiguration(
                                "orders",
                                "orders_3857",
                                "location",
                                new CrsReference("EPSG", 3857)
                        )
                ),
                Map.of("orders", source),
                context(issues)
        );

        assertFalse(issues.hasErrors(), () -> issues.codes.toString());
        CanvasTableSchema transformed = result.propagatedTables().get("orders_3857").schema();
        assertEquals(List.of("order_id", "location"),
                transformed.columns().stream().map(CanvasColumnSchema::name).toList());
        assertEquals(
                new CrsReference("EPSG", 3857),
                transformed.columns().get(1).geometry().crs()
        );
        assertTrue(result.propagatedTables().containsKey("orders"));
    }

    @Test
    void buildsAllSupportedSpatialPredicatesAsInnerJoins() {
        SparkCanvasTable orders = table(
                "orders",
                List.of(
                        scalar("order_id"),
                        geometry("location", GeometryKind.POINT, 4326)
                )
        );
        SparkCanvasTable regions = table(
                "regions",
                List.of(
                        scalar("region_id"),
                        geometry("boundary", GeometryKind.POLYGON, 4326)
                )
        );

        for (SpatialPredicate predicate : SpatialPredicate.values()) {
            RecordingIssueSink issues = new RecordingIssueSink();
            String outputTableName = "joined_" + predicate.name().toLowerCase();
            CanvasNodeOperationResult result = new SpatialJoinNodeOperator().apply(
                    new SpatialJoinNodeDefinition(
                            UUID.randomUUID().toString(),
                            predicate.name(),
                            LAYOUT,
                            new SpatialJoinConfiguration(
                                    "orders",
                                    "regions",
                                    outputTableName,
                                    JoinType.INNER,
                                    List.of(new SpatialJoinCondition(
                                            "location",
                                            predicate,
                                            "boundary"
                                    ))
                            )
                    ),
                    Map.of("orders", orders, "regions", regions),
                    context(issues)
            );

            assertFalse(issues.hasErrors(), () -> predicate + ": " + issues.codes);
            assertEquals(
                    List.of("order_id", "location", "region_id", "boundary"),
                    result.propagatedTables().get(outputTableName).schema().columns().stream()
                            .map(CanvasColumnSchema::name)
                            .toList()
            );
        }
    }

    @Test
    void rejectsSpatialJoinAcrossDifferentCrs() {
        SparkCanvasTable orders = table(
                "orders",
                List.of(geometry("location", GeometryKind.POINT, 4326))
        );
        SparkCanvasTable regions = table(
                "regions",
                List.of(geometry("boundary", GeometryKind.POLYGON, 3857))
        );
        RecordingIssueSink issues = new RecordingIssueSink();

        CanvasNodeOperationResult result = new SpatialJoinNodeOperator().apply(
                new SpatialJoinNodeDefinition(
                        UUID.randomUUID().toString(),
                        "空间连接",
                        LAYOUT,
                        new SpatialJoinConfiguration(
                                "orders",
                                "regions",
                                "joined",
                                JoinType.INNER,
                                List.of(new SpatialJoinCondition(
                                        "location",
                                        SpatialPredicate.WITHIN,
                                        "boundary"
                                ))
                        )
                ),
                Map.of("orders", orders, "regions", regions),
                context(issues)
        );

        assertTrue(issues.codes.contains("SPATIAL_CRS_MISMATCH"));
        assertTrue(result.propagatedTables().isEmpty());
    }

    @Test
    void reportsUnsupportedGeometryCrsAndDimensionBeforeSparkAnalysis() {
        CanvasColumnSchema unsupported = new CanvasColumnSchema(
                "location",
                PlatformDataType.GEOMETRY,
                null,
                null,
                null,
                true,
                null,
                false,
                false,
                null,
                new GeometryTypeDefinition(
                        GeometryKind.POINT,
                        new CrsReference("ESRI", 102100),
                        CoordinateDimension.XYZ
                )
        );
        RecordingIssueSink issues = new RecordingIssueSink();

        CanvasNodeSupport.validateSupportedGeometry(
                List.of(unsupported),
                "configuration.tableName",
                issues
        );

        assertTrue(issues.codes.contains("UNSUPPORTED_GEOMETRY_CRS"));
        assertTrue(issues.codes.contains("UNSUPPORTED_GEOMETRY_DIMENSION"));
    }

    @Test
    void constructsValidatesMeasuresAndSerializesGeometry() {
        SparkCanvasTable source = table(
                new CanvasTableSchema(
                        "raw",
                        CanvasTableOrigin.jdbc(UUID.randomUUID(), "raw"),
                        List.of(
                                scalar("id"),
                                stringColumn("wkt", false)
                        ),
                        CanvasDatasetKind.UNBOUNDED,
                        null,
                        null
                ),
                List.of(RowFactory.create(1L, "POLYGON ((0 0, 0 1, 1 1, 1 0, 0 0))"))
        );
        RecordingIssueSink constructIssues = new RecordingIssueSink();
        CanvasNodeOperationResult constructed = new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 构造",
                        LAYOUT,
                        new GeometryConstructConfiguration(
                                "raw",
                                "geometry_table",
                                "shape",
                                new GeometryConstructSource.Wkt("wkt"),
                                geometryType(GeometryKind.POLYGON, 4326)
                        )
                ),
                Map.of("raw", source),
                context(constructIssues)
        );

        assertFalse(constructIssues.hasErrors(), () -> constructIssues.codes.toString());
        SparkCanvasTable geometryTable = constructed.propagatedTables().get("geometry_table");
        assertNotNull(geometryTable);
        assertTrue(constructed.propagatedTables().containsKey("raw"));
        assertEquals(CanvasDatasetKind.UNBOUNDED, geometryTable.schema().datasetKind());
        assertEquals(PlatformDataType.GEOMETRY, geometryTable.schema().columns().get(2).fieldType());
        assertFalse(geometryTable.schema().columns().get(2).nullable());
        assertNull(geometryTable.schema().origin());

        RecordingIssueSink validateIssues = new RecordingIssueSink();
        CanvasNodeOperationResult validated = new GeometryValidateNodeOperator().apply(
                new GeometryValidateNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 校验",
                        LAYOUT,
                        new GeometryValidateConfiguration(
                                "geometry_table",
                                "validated",
                                "shape",
                                "is_valid",
                                "invalid_reason"
                        )
                ),
                constructed.propagatedTables(),
                context(validateIssues)
        );
        Row validatedRow = validated.propagatedTables().get("validated").dataset().head();
        CanvasTableSchema validatedSchema = validated.propagatedTables().get("validated").schema();
        assertFalse(validateIssues.hasErrors(), () -> validateIssues.codes.toString());
        assertTrue(validatedRow.getBoolean(validatedRow.fieldIndex("is_valid")));
        assertTrue(validatedRow.isNullAt(validatedRow.fieldIndex("invalid_reason")));
        assertFalse(validatedSchema.columns().get(3).nullable());
        assertTrue(validatedSchema.columns().get(4).nullable());
        assertNull(validatedSchema.origin());

        RecordingIssueSink measureIssues = new RecordingIssueSink();
        CanvasNodeOperationResult measured = new SpatialMeasureNodeOperator().apply(
                new SpatialMeasureNodeDefinition(
                        UUID.randomUUID().toString(),
                        "空间测量",
                        LAYOUT,
                        new SpatialMeasureConfiguration(
                                "geometry_table",
                                "measured",
                                List.of(new SpatialMeasurement.Area(
                                        "shape",
                                        SpatialMeasureMode.PLANAR,
                                        "area"
                                ))
                        )
                ),
                constructed.propagatedTables(),
                context(measureIssues)
        );
        Row measuredRow = measured.propagatedTables().get("measured").dataset().head();
        assertFalse(measureIssues.hasErrors(), () -> measureIssues.codes.toString());
        assertTrue(measureIssues.codes.contains("PLANAR_MEASURE_USES_ANGULAR_UNITS"));
        assertEquals(1d, measuredRow.getDouble(measuredRow.fieldIndex("area")), 0.000001d);
        assertNull(measured.propagatedTables().get("measured").schema().origin());

        RecordingIssueSink serializeIssues = new RecordingIssueSink();
        CanvasNodeOperationResult serialized = new GeometrySerializeNodeOperator().apply(
                new GeometrySerializeNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 序列化",
                        LAYOUT,
                        new GeometrySerializeConfiguration(
                                "geometry_table",
                                "serialized",
                                "shape",
                                "shape_wkt",
                                GeometrySerializationFormat.WKT
                        )
                ),
                constructed.propagatedTables(),
                context(serializeIssues)
        );
        Row serializedRow = serialized.propagatedTables().get("serialized").dataset().head();
        assertFalse(serializeIssues.hasErrors(), () -> serializeIssues.codes.toString());
        assertTrue(serializedRow.getString(serializedRow.fieldIndex("shape_wkt"))
                .startsWith("POLYGON"));
        assertFalse(serialized.propagatedTables().get("serialized")
                .schema().columns().get(3).nullable());
        assertNull(serialized.propagatedTables().get("serialized").schema().origin());
    }

    @Test
    void constructsGeometryFromWktWkbGeoJsonAndNumericCoordinates() {
        GeometryFactory geometryFactory = new GeometryFactory();
        byte[] wkb = new WKBWriter().write(
                geometryFactory.createPoint(new Coordinate(1d, 2d))
        );
        SparkCanvasTable source = table(
                new CanvasTableSchema(
                        "raw",
                        null,
                        List.of(
                                stringColumn("wkt", false),
                                typedColumn("wkb", PlatformDataType.BINARY, false),
                                stringColumn("geojson", false),
                                typedColumn("x", PlatformDataType.INTEGER, false),
                                typedColumn("y", PlatformDataType.LONG, false)
                        ),
                        CanvasDatasetKind.BOUNDED,
                        null,
                        null
                ),
                List.of(RowFactory.create(
                        "POINT (1 2)",
                        wkb,
                        "{\"type\":\"Point\",\"coordinates\":[1,2]}",
                        1,
                        2L
                ))
        );
        List<GeometryConstructSource> sources = List.of(
                new GeometryConstructSource.Wkt("wkt"),
                new GeometryConstructSource.Wkb("wkb"),
                new GeometryConstructSource.GeoJson("geojson"),
                new GeometryConstructSource.PointFromXy("x", "y")
        );

        for (int index = 0; index < sources.size(); index++) {
            RecordingIssueSink issues = new RecordingIssueSink();
            String outputTableName = "geometry_" + index;
            CanvasNodeOperationResult result = new GeometryConstructNodeOperator().apply(
                    new GeometryConstructNodeDefinition(
                            UUID.randomUUID().toString(),
                            "Geometry 构造",
                            LAYOUT,
                            new GeometryConstructConfiguration(
                                    "raw",
                                    outputTableName,
                                    "shape",
                                    sources.get(index),
                                    geometryType(GeometryKind.POINT, 4326)
                            )
                    ),
                    Map.of("raw", source),
                    context(issues)
            );

            Geometry geometry = result.propagatedTables().get(outputTableName)
                    .dataset().head().getAs("shape");
            assertFalse(issues.hasErrors(), () -> issues.codes.toString());
            assertEquals("Point", geometry.getGeometryType());
            assertEquals(4326, geometry.getSRID());
            assertEquals(1d, geometry.getCoordinate().getX(), 0.000001d);
            assertEquals(2d, geometry.getCoordinate().getY(), 0.000001d);
        }
    }

    @Test
    void calculatesAllSpatialMeasurementKindsInPlanarAndSpheroidModes() {
        SparkCanvasTable current = table(
                new CanvasTableSchema(
                        "raw",
                        null,
                        List.of(
                                stringColumn("polygon_wkt", false),
                                stringColumn("line_wkt", false),
                                stringColumn("point_a_wkt", false),
                                stringColumn("point_b_wkt", false)
                        ),
                        CanvasDatasetKind.BOUNDED,
                        null,
                        null
                ),
                List.of(RowFactory.create(
                        "POLYGON ((0 0, 0 1, 1 1, 1 0, 0 0))",
                        "LINESTRING (0 0, 0 1)",
                        "POINT (0 0)",
                        "POINT (0 1)"
                ))
        );
        String currentName = "raw";
        String[][] geometries = {
                {"polygon_wkt", "polygon", "POLYGON"},
                {"line_wkt", "line", "LINESTRING"},
                {"point_a_wkt", "point_a", "POINT"},
                {"point_b_wkt", "point_b", "POINT"}
        };
        for (int index = 0; index < geometries.length; index++) {
            String outputName = "geometry_step_" + index;
            GeometryKind kind = GeometryKind.valueOf(geometries[index][2]);
            RecordingIssueSink issues = new RecordingIssueSink();
            CanvasNodeOperationResult result = new GeometryConstructNodeOperator().apply(
                    new GeometryConstructNodeDefinition(
                            UUID.randomUUID().toString(),
                            "Geometry 构造",
                            LAYOUT,
                            new GeometryConstructConfiguration(
                                    currentName,
                                    outputName,
                                    geometries[index][1],
                                    new GeometryConstructSource.Wkt(geometries[index][0]),
                                    geometryType(kind, 4326)
                            )
                    ),
                    Map.of(currentName, current),
                    context(issues)
            );
            assertFalse(issues.hasErrors(), () -> issues.codes.toString());
            current = result.propagatedTables().get(outputName);
            currentName = outputName;
        }

        List<SpatialMeasurement> measurements = List.of(
                new SpatialMeasurement.Area(
                        "polygon", SpatialMeasureMode.PLANAR, "area_planar"),
                new SpatialMeasurement.Length(
                        "line", SpatialMeasureMode.PLANAR, "length_planar"),
                new SpatialMeasurement.Perimeter(
                        "polygon", SpatialMeasureMode.PLANAR, "perimeter_planar"),
                new SpatialMeasurement.Distance(
                        "point_a", "point_b", SpatialMeasureMode.PLANAR, "distance_planar"),
                new SpatialMeasurement.X("point_b", "point_x"),
                new SpatialMeasurement.Y("point_b", "point_y"),
                new SpatialMeasurement.Area(
                        "polygon", SpatialMeasureMode.SPHEROID, "area_spheroid"),
                new SpatialMeasurement.Length(
                        "line", SpatialMeasureMode.SPHEROID, "length_spheroid"),
                new SpatialMeasurement.Perimeter(
                        "polygon", SpatialMeasureMode.SPHEROID, "perimeter_spheroid"),
                new SpatialMeasurement.Distance(
                        "point_a", "point_b", SpatialMeasureMode.SPHEROID,
                        "distance_spheroid")
        );
        RecordingIssueSink issues = new RecordingIssueSink();
        CanvasNodeOperationResult result = new SpatialMeasureNodeOperator().apply(
                new SpatialMeasureNodeDefinition(
                        UUID.randomUUID().toString(),
                        "空间测量",
                        LAYOUT,
                        new SpatialMeasureConfiguration(currentName, "measured", measurements)
                ),
                Map.of(currentName, current),
                context(issues)
        );
        SparkCanvasTable measured = result.propagatedTables().get("measured");
        Row row = measured.dataset().head();

        assertFalse(issues.hasErrors(), () -> issues.codes.toString());
        assertEquals(1d, row.getDouble(row.fieldIndex("area_planar")), 0.000001d);
        assertEquals(1d, row.getDouble(row.fieldIndex("length_planar")), 0.000001d);
        assertEquals(4d, row.getDouble(row.fieldIndex("perimeter_planar")), 0.000001d);
        assertEquals(1d, row.getDouble(row.fieldIndex("distance_planar")), 0.000001d);
        assertEquals(0d, row.getDouble(row.fieldIndex("point_x")), 0.000001d);
        assertEquals(1d, row.getDouble(row.fieldIndex("point_y")), 0.000001d);
        assertTrue(row.getDouble(row.fieldIndex("area_spheroid")) > 12_000_000_000d);
        assertTrue(row.getDouble(row.fieldIndex("area_spheroid")) < 13_000_000_000d);
        assertTrue(row.getDouble(row.fieldIndex("length_spheroid")) > 100_000d);
        assertTrue(row.getDouble(row.fieldIndex("length_spheroid")) < 112_000d);
        assertTrue(row.getDouble(row.fieldIndex("perimeter_spheroid")) > 400_000d);
        assertTrue(row.getDouble(row.fieldIndex("perimeter_spheroid")) < 450_000d);
        assertTrue(row.getDouble(row.fieldIndex("distance_spheroid")) > 100_000d);
        assertTrue(row.getDouble(row.fieldIndex("distance_spheroid")) < 112_000d);
        assertEquals(
                measurements.stream().map(SpatialMeasurement::outputColumnName).toList(),
                measured.schema().columns().stream()
                        .skip(measured.schema().columns().size() - measurements.size())
                        .map(CanvasColumnSchema::name)
                        .toList()
        );
        assertTrue(measured.schema().columns().stream()
                .skip(measured.schema().columns().size() - measurements.size())
                .allMatch(CanvasColumnSchema::nullable));
    }

    @Test
    void serializesGeometryAsWktWkbAndGeoJson() {
        SparkCanvasTable source = table(
                new CanvasTableSchema(
                        "raw",
                        null,
                        List.of(stringColumn("wkt", false)),
                        CanvasDatasetKind.BOUNDED,
                        null,
                        null
                ),
                List.of(RowFactory.create("POINT (1 2)"))
        );
        CanvasNodeOperationResult constructed = new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 构造",
                        LAYOUT,
                        new GeometryConstructConfiguration(
                                "raw", "geometry_table", "shape",
                                new GeometryConstructSource.Wkt("wkt"),
                                geometryType(GeometryKind.POINT, 4326)
                        )
                ),
                Map.of("raw", source),
                context(new RecordingIssueSink())
        );
        SparkCanvasTable geometryTable = constructed.propagatedTables().get("geometry_table");

        for (GeometrySerializationFormat format : GeometrySerializationFormat.values()) {
            RecordingIssueSink issues = new RecordingIssueSink();
            String outputTableName = "serialized_" + format.name().toLowerCase();
            CanvasNodeOperationResult result = new GeometrySerializeNodeOperator().apply(
                    new GeometrySerializeNodeDefinition(
                            UUID.randomUUID().toString(),
                            "Geometry 序列化",
                            LAYOUT,
                            new GeometrySerializeConfiguration(
                                    "geometry_table",
                                    outputTableName,
                                    "shape",
                                    "serialized_value",
                                    format
                            )
                    ),
                    Map.of("geometry_table", geometryTable),
                    context(issues)
            );
            SparkCanvasTable serialized = result.propagatedTables().get(outputTableName);
            Object value = serialized.dataset().head().getAs("serialized_value");

            assertFalse(issues.hasErrors(), () -> issues.codes.toString());
            if (format == GeometrySerializationFormat.WKB) {
                assertTrue(value instanceof byte[]);
                assertEquals(PlatformDataType.BINARY,
                        serialized.schema().columns().getLast().fieldType());
            } else {
                assertTrue(value instanceof String);
                assertEquals(PlatformDataType.STRING,
                        serialized.schema().columns().getLast().fieldType());
                assertTrue(((String) value).toUpperCase().contains("POINT"));
            }
        }
    }

    @Test
    void failsMalformedGeometryAndDeclaredKindMismatchAtRuntime() {
        SparkCanvasTable source = table(
                new CanvasTableSchema(
                        "raw",
                        null,
                        List.of(
                                stringColumn("malformed", false),
                                stringColumn("point", false)
                        ),
                        CanvasDatasetKind.BOUNDED,
                        null,
                        null
                ),
                List.of(RowFactory.create("NOT_A_GEOMETRY", "POINT (1 2)"))
        );

        CanvasNodeOperationResult malformed = new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 构造",
                        LAYOUT,
                        new GeometryConstructConfiguration(
                                "raw", "malformed_output", "shape",
                                new GeometryConstructSource.Wkt("malformed"),
                                geometryType(GeometryKind.POINT, 4326)
                        )
                ),
                Map.of("raw", source),
                context(new RecordingIssueSink())
        );
        CanvasNodeOperationResult kindMismatch = new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 构造",
                        LAYOUT,
                        new GeometryConstructConfiguration(
                                "raw", "kind_output", "shape",
                                new GeometryConstructSource.Wkt("point"),
                                geometryType(GeometryKind.POLYGON, 4326)
                        )
                ),
                Map.of("raw", source),
                context(new RecordingIssueSink())
        );

        assertThrows(
                Exception.class,
                () -> malformed.propagatedTables().get("malformed_output").dataset().head()
        );
        Exception mismatch = assertThrows(
                Exception.class,
                () -> kindMismatch.propagatedTables().get("kind_output").dataset().head()
        );
        assertTrue(exceptionMessages(mismatch).contains("GEOMETRY_CONSTRUCT_KIND_MISMATCH"));
    }

    @Test
    void propagatesNullAcrossSpatialFoundationProcessors() {
        SparkCanvasTable source = table(
                new CanvasTableSchema(
                        "raw",
                        null,
                        List.of(stringColumn("wkt", true)),
                        CanvasDatasetKind.BOUNDED,
                        null,
                        null
                ),
                java.util.Collections.singletonList(RowFactory.create((Object) null))
        );
        CanvasNodeOperationResult constructed = new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 构造",
                        LAYOUT,
                        new GeometryConstructConfiguration(
                                "raw",
                                "geometry_table",
                                "shape",
                                new GeometryConstructSource.Wkt("wkt"),
                                geometryType(GeometryKind.POINT, 4326)
                        )
                ),
                Map.of("raw", source),
                context(new RecordingIssueSink())
        );
        SparkCanvasTable geometryTable = constructed.propagatedTables().get("geometry_table");
        assertTrue(geometryTable.dataset().head().isNullAt(1));

        CanvasNodeOperationResult validated = new GeometryValidateNodeOperator().apply(
                new GeometryValidateNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 校验",
                        LAYOUT,
                        new GeometryValidateConfiguration(
                                "geometry_table", "validated", "shape", "is_valid", "reason")
                ),
                constructed.propagatedTables(),
                context(new RecordingIssueSink())
        );
        Row validatedRow = validated.propagatedTables().get("validated").dataset().head();
        assertTrue(validatedRow.isNullAt(validatedRow.fieldIndex("is_valid")));
        assertTrue(validatedRow.isNullAt(validatedRow.fieldIndex("reason")));

        CanvasNodeOperationResult measured = new SpatialMeasureNodeOperator().apply(
                new SpatialMeasureNodeDefinition(
                        UUID.randomUUID().toString(),
                        "空间测量",
                        LAYOUT,
                        new SpatialMeasureConfiguration(
                                "geometry_table",
                                "measured",
                                List.of(new SpatialMeasurement.X("shape", "x"))
                        )
                ),
                constructed.propagatedTables(),
                context(new RecordingIssueSink())
        );
        Row measuredRow = measured.propagatedTables().get("measured").dataset().head();
        assertTrue(measuredRow.isNullAt(measuredRow.fieldIndex("x")));

        CanvasNodeOperationResult serialized = new GeometrySerializeNodeOperator().apply(
                new GeometrySerializeNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 序列化",
                        LAYOUT,
                        new GeometrySerializeConfiguration(
                                "geometry_table", "serialized", "shape", "shape_wkt",
                                GeometrySerializationFormat.WKT)
                ),
                constructed.propagatedTables(),
                context(new RecordingIssueSink())
        );
        Row serializedRow = serialized.propagatedTables().get("serialized").dataset().head();
        assertTrue(serializedRow.isNullAt(serializedRow.fieldIndex("shape_wkt")));
    }

    @Test
    void diagnosesInvalidGeometryWithoutDroppingOrFailingTheRow() {
        SparkCanvasTable source = table(
                new CanvasTableSchema(
                        "raw",
                        null,
                        List.of(stringColumn("wkt", false)),
                        CanvasDatasetKind.BOUNDED,
                        null,
                        null
                ),
                List.of(RowFactory.create(
                        "POLYGON ((0 0, 2 2, 0 2, 2 0, 0 0))"
                ))
        );
        CanvasNodeOperationResult constructed = new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 构造",
                        LAYOUT,
                        new GeometryConstructConfiguration(
                                "raw",
                                "geometry_table",
                                "shape",
                                new GeometryConstructSource.Wkt("wkt"),
                                geometryType(GeometryKind.POLYGON, 4326)
                        )
                ),
                Map.of("raw", source),
                context(new RecordingIssueSink())
        );
        RecordingIssueSink issues = new RecordingIssueSink();
        CanvasNodeOperationResult validated = new GeometryValidateNodeOperator().apply(
                new GeometryValidateNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 校验",
                        LAYOUT,
                        new GeometryValidateConfiguration(
                                "geometry_table", "validated", "shape", "is_valid", "reason")
                ),
                constructed.propagatedTables(),
                context(issues)
        );

        List<Row> rows = validated.propagatedTables().get("validated").dataset().collectAsList();

        assertFalse(issues.hasErrors(), () -> issues.codes.toString());
        assertEquals(1, rows.size());
        assertFalse(rows.getFirst().getBoolean(rows.getFirst().fieldIndex("is_valid")));
        assertNotNull(rows.getFirst().getString(rows.getFirst().fieldIndex("reason")));
    }

    @Test
    void repairsBuffersAndExplodesGeometryWithStableSchemas() {
        SparkCanvasTable invalidPolygon = constructWktTable(
                "POLYGON ((0 0, 2 2, 0 2, 2 0, 0 0))",
                GeometryKind.POLYGON,
                "invalid_polygon"
        );
        RecordingIssueSink repairIssues = new RecordingIssueSink();
        CanvasNodeOperationResult repaired = new GeometryRepairNodeOperator().apply(
                new GeometryRepairNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 修复",
                        LAYOUT,
                        new GeometryRepairConfiguration(
                                "invalid_polygon", "repaired", "shape", "repaired_shape")
                ),
                Map.of("invalid_polygon", invalidPolygon),
                context(repairIssues)
        );
        SparkCanvasTable repairedTable = repaired.propagatedTables().get("repaired");
        Geometry repairedGeometry = repairedTable.dataset().head().getAs("repaired_shape");

        assertFalse(repairIssues.hasErrors(), () -> repairIssues.codes.toString());
        assertTrue(repairedGeometry.isValid());
        assertEquals(4326, repairedGeometry.getSRID());
        assertEquals(GeometryKind.GEOMETRY,
                repairedTable.schema().columns().getLast().geometry().kind());
        assertNull(repairedTable.schema().origin());

        SparkCanvasTable point = constructWktTable(
                "POINT (0 0)", GeometryKind.POINT, "point_table");
        for (SpatialMeasureMode mode : SpatialMeasureMode.values()) {
            RecordingIssueSink bufferIssues = new RecordingIssueSink();
            String outputName = "buffered_" + mode.name().toLowerCase();
            CanvasNodeOperationResult buffered = new GeometryBufferNodeOperator().apply(
                    new GeometryBufferNodeDefinition(
                            UUID.randomUUID().toString(),
                            "Geometry Buffer",
                            LAYOUT,
                            new GeometryBufferConfiguration(
                                    "point_table",
                                    outputName,
                                    "shape",
                                    "buffer_shape",
                                    mode == SpatialMeasureMode.SPHEROID ? 1000d : 1d,
                                    mode
                            )
                    ),
                    Map.of("point_table", point),
                    context(bufferIssues)
            );
            SparkCanvasTable bufferedTable = buffered.propagatedTables().get(outputName);
            Geometry bufferGeometry = bufferedTable.dataset().head().getAs("buffer_shape");

            assertFalse(bufferIssues.hasErrors(), () -> bufferIssues.codes.toString());
            assertEquals("MultiPolygon", bufferGeometry.getGeometryType());
            assertFalse(bufferGeometry.isEmpty());
            assertEquals(4326, bufferGeometry.getSRID());
            assertEquals(GeometryKind.MULTIPOLYGON,
                    bufferedTable.schema().columns().getLast().geometry().kind());
            if (mode == SpatialMeasureMode.PLANAR) {
                assertTrue(bufferIssues.codes.contains("PLANAR_BUFFER_USES_ANGULAR_UNITS"));
            }
        }

        SparkCanvasTable multiPolygon = constructWktTable(
                "MULTIPOLYGON (((0 0, 0 1, 1 1, 1 0, 0 0)), "
                        + "((2 2, 2 3, 3 3, 3 2, 2 2)))",
                GeometryKind.MULTIPOLYGON,
                "multi_polygon"
        );
        RecordingIssueSink explodeIssues = new RecordingIssueSink();
        CanvasNodeOperationResult exploded = new GeometryExplodeNodeOperator().apply(
                new GeometryExplodeNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 拆分",
                        LAYOUT,
                        new GeometryExplodeConfiguration(
                                "multi_polygon", "parts", "shape", "part", "part_index")
                ),
                Map.of("multi_polygon", multiPolygon),
                context(explodeIssues)
        );
        SparkCanvasTable parts = exploded.propagatedTables().get("parts");
        List<Row> partRows = parts.dataset().orderBy("part_index").collectAsList();

        assertFalse(explodeIssues.hasErrors(), () -> explodeIssues.codes.toString());
        assertEquals(2, partRows.size());
        assertEquals(List.of(0, 1), partRows.stream().map(row -> row.getAs("part_index")).toList());
        assertTrue(partRows.stream().map(row -> (Geometry) row.getAs("part"))
                .allMatch(geometry -> "Polygon".equals(geometry.getGeometryType())
                        && geometry.getSRID() == 4326));
        assertEquals(GeometryKind.POLYGON,
                parts.schema().columns().get(parts.schema().columns().size() - 2).geometry().kind());
        assertEquals(PlatformDataType.INTEGER, parts.schema().columns().getLast().fieldType());
        assertTrue(parts.schema().columns().getLast().nullable());
        assertNull(parts.schema().origin());
    }

    @Test
    void clipsSourceGeometryWithPolygonMasksAndKeepsOnlyNonEmptyMatches() {
        SparkCanvasTable sourceRaw = table(
                new CanvasTableSchema(
                        "source_raw",
                        null,
                        List.of(scalar("feature_id"), stringColumn("wkt", true)),
                        CanvasDatasetKind.BOUNDED,
                        null,
                        null
                ),
                List.of(
                        RowFactory.create(1L, "LINESTRING (-1 1, 3 1)"),
                        RowFactory.create(2L, "LINESTRING (10 10, 11 11)"),
                        RowFactory.create(3L, null),
                        RowFactory.create(4L, "LINESTRING EMPTY")
                )
        );
        SparkCanvasTable maskRaw = table(
                new CanvasTableSchema(
                        "mask_raw",
                        null,
                        List.of(scalar("mask_id"), stringColumn("wkt", false)),
                        CanvasDatasetKind.BOUNDED,
                        null,
                        null
                ),
                List.of(
                        RowFactory.create(10L, "POLYGON ((0 0, 0 2, 2 2, 2 0, 0 0))"),
                        RowFactory.create(11L, "POLYGON ((1 0, 1 2, 3 2, 3 0, 1 0))")
                )
        );
        CanvasNodeOperationResult sourceConstructed = new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(
                        UUID.randomUUID().toString(),
                        "来源 Geometry",
                        LAYOUT,
                        new GeometryConstructConfiguration(
                                "source_raw", "source", "shape",
                                new GeometryConstructSource.Wkt("wkt"),
                                geometryType(GeometryKind.LINESTRING, 4326)
                        )
                ),
                Map.of("source_raw", sourceRaw),
                context(new RecordingIssueSink())
        );
        CanvasNodeOperationResult maskConstructed = new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Mask Geometry",
                        LAYOUT,
                        new GeometryConstructConfiguration(
                                "mask_raw", "mask", "boundary",
                                new GeometryConstructSource.Wkt("wkt"),
                                geometryType(GeometryKind.POLYGON, 4326)
                        )
                ),
                Map.of("mask_raw", maskRaw),
                context(new RecordingIssueSink())
        );
        Map<String, SparkCanvasTable> inputs = new LinkedHashMap<>();
        inputs.put("source", sourceConstructed.propagatedTables().get("source"));
        inputs.put("mask", maskConstructed.propagatedTables().get("mask"));
        RecordingIssueSink issues = new RecordingIssueSink();

        CanvasNodeOperationResult clipped = new SpatialClipNodeOperator().apply(
                new SpatialClipNodeDefinition(
                        UUID.randomUUID().toString(),
                        "空间裁剪",
                        LAYOUT,
                        new SpatialClipConfiguration(
                                "source", "mask", "clipped",
                                "shape", "boundary", "clipped_shape")
                ),
                inputs,
                context(issues)
        );

        SparkCanvasTable clippedTable = clipped.propagatedTables().get("clipped");
        List<Row> rows = clippedTable.dataset().collectAsList();
        assertFalse(issues.hasErrors(), () -> issues.codes.toString());
        assertEquals(2, rows.size());
        assertTrue(rows.stream().allMatch(row -> row.getLong(row.fieldIndex("feature_id")) == 1L));
        assertTrue(rows.stream().map(row -> (Geometry) row.getAs("clipped_shape"))
                .allMatch(geometry -> !geometry.isEmpty() && geometry.getSRID() == 4326));
        assertEquals(
                List.of("feature_id", "wkt", "shape", "clipped_shape"),
                clippedTable.schema().columns().stream().map(CanvasColumnSchema::name).toList()
        );
        assertEquals(GeometryKind.GEOMETRY,
                clippedTable.schema().columns().getLast().geometry().kind());
        assertFalse(clippedTable.schema().columns().getLast().nullable());
        assertNull(clippedTable.schema().origin());
    }

    @Test
    void aggregatesGeometryWithStableGroupingNullAndEmptySemantics() {
        SparkCanvasTable raw = table(
                new CanvasTableSchema(
                        "raw",
                        null,
                        List.of(stringColumn("district", false), stringColumn("wkt", true)),
                        CanvasDatasetKind.BOUNDED,
                        null,
                        null
                ),
                List.of(
                        RowFactory.create("A", "POLYGON ((0 0, 0 2, 2 2, 2 0, 0 0))"),
                        RowFactory.create("A", "POLYGON ((1 1, 1 3, 3 3, 3 1, 1 1))"),
                        RowFactory.create("A", null),
                        RowFactory.create("B", "POLYGON EMPTY"),
                        RowFactory.create("B", "POLYGON ((0 0, 0 1, 1 1, 1 0, 0 0))"),
                        RowFactory.create("C", null),
                        RowFactory.create("D", "POLYGON EMPTY")
                )
        );
        CanvasNodeOperationResult constructed = new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 构造",
                        LAYOUT,
                        new GeometryConstructConfiguration(
                                "raw", "parcels", "shape",
                                new GeometryConstructSource.Wkt("wkt"),
                                geometryType(GeometryKind.POLYGON, 4326)
                        )
                ),
                Map.of("raw", raw),
                context(new RecordingIssueSink())
        );
        RecordingIssueSink issues = new RecordingIssueSink();
        List<SpatialAggregation> aggregations = List.of(
                new SpatialAggregation(SpatialAggregationKind.UNION, "shape", "union_shape"),
                new SpatialAggregation(
                        SpatialAggregationKind.INTERSECTION, "shape", "intersection_shape"),
                new SpatialAggregation(SpatialAggregationKind.COLLECT, "shape", "collect_shape"),
                new SpatialAggregation(SpatialAggregationKind.ENVELOPE, "shape", "envelope_shape")
        );

        CanvasNodeOperationResult aggregated = new SpatialAggregateNodeOperator().apply(
                new SpatialAggregateNodeDefinition(
                        UUID.randomUUID().toString(),
                        "空间聚合",
                        LAYOUT,
                        new SpatialAggregateConfiguration(
                                "parcels", "districts", List.of("district"), aggregations)
                ),
                constructed.propagatedTables(),
                context(issues)
        );

        SparkCanvasTable output = aggregated.propagatedTables().get("districts");
        Map<String, Row> rows = new LinkedHashMap<>();
        for (Row row : output.dataset().collectAsList()) {
            rows.put(row.getString(row.fieldIndex("district")), row);
        }
        assertFalse(issues.hasErrors(), () -> issues.codes.toString());
        assertEquals(4, rows.size());
        assertEquals(7d, ((Geometry) rows.get("A").getAs("union_shape")).getArea(), 0.000001d);
        assertEquals(1d,
                ((Geometry) rows.get("A").getAs("intersection_shape")).getArea(), 0.000001d);
        assertFalse(((Geometry) rows.get("A").getAs("collect_shape")).isEmpty());
        assertEquals(9d,
                ((Geometry) rows.get("A").getAs("envelope_shape")).getArea(), 0.000001d);
        assertTrue(((Geometry) rows.get("B").getAs("intersection_shape")).isEmpty());
        assertTrue(rows.get("C").isNullAt(rows.get("C").fieldIndex("union_shape")));
        assertTrue(rows.get("C").isNullAt(rows.get("C").fieldIndex("intersection_shape")));
        assertTrue(rows.get("C").isNullAt(rows.get("C").fieldIndex("collect_shape")));
        assertTrue(rows.get("C").isNullAt(rows.get("C").fieldIndex("envelope_shape")));
        assertTrue(((Geometry) rows.get("D").getAs("union_shape")).isEmpty());
        assertTrue(((Geometry) rows.get("D").getAs("intersection_shape")).isEmpty());
        assertTrue(rows.get("D").isNullAt(rows.get("D").fieldIndex("envelope_shape")));
        for (Row row : rows.values()) {
            for (String name : List.of(
                    "union_shape", "intersection_shape", "collect_shape", "envelope_shape")) {
                if (!row.isNullAt(row.fieldIndex(name))) {
                    assertEquals(4326, ((Geometry) row.getAs(name)).getSRID());
                }
            }
        }
        assertEquals(
                List.of(
                        "district", "union_shape", "intersection_shape",
                        "collect_shape", "envelope_shape"),
                output.schema().columns().stream().map(CanvasColumnSchema::name).toList()
        );
        assertTrue(output.schema().columns().subList(1, 5).stream().allMatch(column ->
                column.fieldType() == PlatformDataType.GEOMETRY
                        && column.geometry().kind() == GeometryKind.GEOMETRY
                        && column.nullable()));
        assertEquals(CanvasDatasetKind.BOUNDED, output.schema().datasetKind());
        assertNull(output.schema().eventTimeColumn());
        assertNull(output.schema().watermarkDelay());
    }

    @Test
    void preservesNullAndEmptyRowsWhenExplodingGeometry() {
        SparkCanvasTable raw = table(
                new CanvasTableSchema(
                        "raw",
                        null,
                        List.of(scalar("id"), stringColumn("wkt", true)),
                        CanvasDatasetKind.BOUNDED,
                        null,
                        null
                ),
                List.of(
                        RowFactory.create(1L, null),
                        RowFactory.create(2L, "MULTIPOLYGON EMPTY")
                )
        );
        CanvasNodeOperationResult constructed = new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 构造",
                        LAYOUT,
                        new GeometryConstructConfiguration(
                                "raw", "geometry_table", "shape",
                                new GeometryConstructSource.Wkt("wkt"),
                                geometryType(GeometryKind.MULTIPOLYGON, 4326)
                        )
                ),
                Map.of("raw", raw),
                context(new RecordingIssueSink())
        );
        CanvasNodeOperationResult exploded = new GeometryExplodeNodeOperator().apply(
                new GeometryExplodeNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 拆分",
                        LAYOUT,
                        new GeometryExplodeConfiguration(
                                "geometry_table", "parts", "shape", "part", "part_index")
                ),
                constructed.propagatedTables(),
                context(new RecordingIssueSink())
        );

        List<Row> rows = exploded.propagatedTables().get("parts").dataset()
                .orderBy("id").collectAsList();

        assertEquals(2, rows.size());
        assertTrue(rows.stream().allMatch(row -> row.isNullAt(row.fieldIndex("part"))));
        assertTrue(rows.stream().allMatch(row -> row.isNullAt(row.fieldIndex("part_index"))));
    }

    @Test
    void reportsStableSpatialFoundationConfigurationErrors() {
        SparkCanvasTable source = table(
                "source",
                List.of(
                        scalar("not_text"),
                        geometry("web_mercator", GeometryKind.POINT, 3857)
                )
        );
        RecordingIssueSink constructIssues = new RecordingIssueSink();
        new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 构造",
                        LAYOUT,
                        new GeometryConstructConfiguration(
                                "source",
                                "constructed",
                                "shape",
                                new GeometryConstructSource.Wkt("not_text"),
                                geometryType(GeometryKind.POINT, 4326)
                        )
                ),
                Map.of("source", source),
                context(constructIssues)
        );
        assertTrue(constructIssues.codes.contains("GEOMETRY_CONSTRUCT_SOURCE_TYPE_MISMATCH"));

        RecordingIssueSink measureIssues = new RecordingIssueSink();
        new SpatialMeasureNodeOperator().apply(
                new SpatialMeasureNodeDefinition(
                        UUID.randomUUID().toString(),
                        "空间测量",
                        LAYOUT,
                        new SpatialMeasureConfiguration(
                                "source",
                                "measured",
                                List.of(new SpatialMeasurement.Distance(
                                        "web_mercator",
                                        "web_mercator",
                                        SpatialMeasureMode.SPHEROID,
                                        "distance"
                                ))
                        )
                ),
                Map.of("source", source),
                context(measureIssues)
        );
        assertTrue(measureIssues.codes.contains("SPHEROID_MEASURE_REQUIRES_WGS84"));

        RecordingIssueSink serializeIssues = new RecordingIssueSink();
        new GeometrySerializeNodeOperator().apply(
                new GeometrySerializeNodeDefinition(
                        UUID.randomUUID().toString(),
                        "GeoJSON",
                        LAYOUT,
                        new GeometrySerializeConfiguration(
                                "source",
                                "serialized",
                                "web_mercator",
                                "geojson",
                                GeometrySerializationFormat.GEOJSON
                        )
                ),
                Map.of("source", source),
                context(serializeIssues)
        );
        assertTrue(serializeIssues.codes.contains("GEOJSON_REQUIRES_WGS84"));

        RecordingIssueSink bufferIssues = new RecordingIssueSink();
        new GeometryBufferNodeOperator().apply(
                new GeometryBufferNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry Buffer",
                        LAYOUT,
                        new GeometryBufferConfiguration(
                                "source",
                                "buffered",
                                "web_mercator",
                                "buffer_geometry",
                                0d,
                                SpatialMeasureMode.SPHEROID
                        )
                ),
                Map.of("source", source),
                context(bufferIssues)
        );
        assertTrue(bufferIssues.codes.contains("INVALID_GEOMETRY_BUFFER_DISTANCE"));
        assertTrue(bufferIssues.codes.contains("SPHEROID_BUFFER_REQUIRES_WGS84"));
    }

    private SparkCanvasTable constructWktTable(
            String wkt,
            GeometryKind kind,
            String outputTableName
    ) {
        SparkCanvasTable raw = table(
                new CanvasTableSchema(
                        "raw",
                        null,
                        List.of(stringColumn("wkt", false)),
                        CanvasDatasetKind.BOUNDED,
                        null,
                        null
                ),
                List.of(RowFactory.create(wkt))
        );
        CanvasNodeOperationResult result = new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 构造",
                        LAYOUT,
                        new GeometryConstructConfiguration(
                                "raw", outputTableName, "shape",
                                new GeometryConstructSource.Wkt("wkt"),
                                geometryType(kind, 4326)
                        )
                ),
                Map.of("raw", raw),
                context(new RecordingIssueSink())
        );
        return result.propagatedTables().get(outputTableName);
    }

    private CanvasNodeOperationContext context(RecordingIssueSink issues) {
        return new CanvasNodeOperationContext(
                spark,
                MetadataIndex.create(new MetadataSnapshot(List.of(), List.of())),
                issues,
                new SchemaOnlyCanvasNodeDataAccess(spark)
        );
    }

    private SparkCanvasTable table(String name, List<CanvasColumnSchema> columns) {
        CanvasTableSchema schema = new CanvasTableSchema(name, null, columns);
        return table(schema, List.of());
    }

    private SparkCanvasTable table(CanvasTableSchema schema, List<Row> rows) {
        return new SparkCanvasTable(
                schema,
                spark.createDataFrame(rows, SparkTypeMapper.toStructType(schema.columns()))
        );
    }

    private static CanvasColumnSchema scalar(String name) {
        return new CanvasColumnSchema(
                name,
                PlatformDataType.LONG,
                null,
                null,
                null,
                false,
                null,
                false,
                false,
                null
        );
    }

    private static CanvasColumnSchema stringColumn(String name, boolean nullable) {
        return typedColumn(name, PlatformDataType.STRING, nullable);
    }

    private static CanvasColumnSchema typedColumn(
            String name,
            PlatformDataType type,
            boolean nullable
    ) {
        return new CanvasColumnSchema(
                name,
                type,
                null,
                null,
                null,
                nullable,
                null,
                false,
                false,
                null
        );
    }

    private static CanvasColumnSchema geometry(String name, GeometryKind kind, int epsgCode) {
        return new CanvasColumnSchema(
                name,
                PlatformDataType.GEOMETRY,
                null,
                null,
                null,
                true,
                null,
                false,
                false,
                null,
                geometryType(kind, epsgCode)
        );
    }

    private static GeometryTypeDefinition geometryType(GeometryKind kind, int epsgCode) {
        return new GeometryTypeDefinition(
                kind,
                new CrsReference("EPSG", epsgCode),
                CoordinateDimension.XY
        );
    }

    private static String exceptionMessages(Throwable throwable) {
        StringBuilder messages = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null) messages.append(current.getMessage()).append('\n');
            current = current.getCause();
        }
        return messages.toString();
    }

    private static final class RecordingIssueSink implements CanvasNodeIssueSink {
        private final List<String> codes = new ArrayList<>();
        private boolean errors;

        @Override
        public void error(String code, String message, String path) {
            codes.add(code);
            errors = true;
        }

        @Override
        public void warning(String code, String message, String path) {
            codes.add(code);
        }

        @Override
        public boolean hasErrors() {
            return errors;
        }
    }
}
