package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.ConnectionKind;
import cn.superhuang.data.scalpel.contract.task.DataSourcePurpose;
import cn.superhuang.data.scalpel.contract.task.FileOutputConfiguration;
import cn.superhuang.data.scalpel.contract.task.FileOutputConflictPolicy;
import cn.superhuang.data.scalpel.contract.task.FileOutputFormatOptions;
import cn.superhuang.data.scalpel.contract.task.FileOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasPreparedFileOutput;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeDataSource;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeS3Connection;
import cn.superhuang.datascalpel.taskengine.http.JsonSupport;
import cn.superhuang.datascalpel.taskengine.spark.SedonaSparkSupport;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.expr;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GeoJsonFileOutputWriterTest {
    private SparkSession spark;

    @BeforeAll
    void startSpark() {
        spark = SedonaSparkSupport.initialize(SedonaSparkSupport.builder()
                .master("local[2]")
                .appName("geojson-file-output-writer-test")
                .config("spark.ui.enabled", "false")
                .config("spark.driver.host", "127.0.0.1")
                .config("spark.driver.bindAddress", "127.0.0.1")
                .config("spark.sql.caseSensitive", "true")
                .config("spark.sql.session.timeZone", "UTC")
                .getOrCreate());
    }

    @AfterAll
    void stopSpark() {
        if (spark != null) spark.stop();
    }

    @Test
    void writesRfc7946FeatureCollectionWithIdsNullGeometryAndTypedProperties(
            @TempDir Path directory
    ) throws Exception {
        Dataset<Row> dataset = dataset();
        FileOutputFormatOptions.GeoJson options = new FileOutputFormatOptions.GeoJson(
                "districts", "geom", "district_id", false);
        CanvasPreparedFileOutput output = preparedOutput(dataset, options);

        Path artifact = GeoJsonFileOutputWriter.writeLocal(
                directory, output, dataset, options, GeoJsonFileOutputWriter.MAX_ARTIFACT_BYTES);
        String json = Files.readString(artifact);
        JsonNode document = JsonSupport.strictObjectMapper().readTree(json);

        assertEquals("FeatureCollection", document.path("type").asText());
        assertEquals(2, document.path("features").size());
        JsonNode first = feature(document, 1L);
        assertEquals("Point", first.path("geometry").path("type").asText());
        assertEquals(120.15d, first.path("geometry").path("coordinates").get(0).asDouble());
        assertEquals("杭州", first.path("properties").path("district_name").asText());
        assertTrue(json.contains("\"amount\":1234.50"));
        assertEquals(0, new java.math.BigDecimal("1234.50")
                .compareTo(first.path("properties").path("amount").decimalValue()));
        assertEquals("2026-08-01", first.path("properties").path("business_date").asText());

        JsonNode second = feature(document, 2L);
        assertTrue(second.path("geometry").isNull());
        assertTrue(second.path("properties").path("active").isNull());
        assertTrue(second.path("properties").path("amount").isNull());
    }

    @Test
    void omitsNullPropertiesWhenConfigured(@TempDir Path directory) throws Exception {
        Dataset<Row> dataset = dataset();
        FileOutputFormatOptions.GeoJson options = new FileOutputFormatOptions.GeoJson(
                "districts", "geom", null, true);
        CanvasPreparedFileOutput output = preparedOutput(dataset, options);

        Path artifact = GeoJsonFileOutputWriter.writeLocal(
                directory, output, dataset, options, GeoJsonFileOutputWriter.MAX_ARTIFACT_BYTES);
        JsonNode document = JsonSupport.strictObjectMapper().readTree(Files.readString(artifact));
        JsonNode second = document.path("features").valueStream()
                .filter(feature -> feature.path("properties").path("district_id").asLong() == 2L)
                .findFirst()
                .orElseThrow();

        assertFalse(second.has("id"));
        assertFalse(second.path("properties").has("active"));
        assertFalse(second.path("properties").has("amount"));
    }

    @Test
    void stopsBeforeConfiguredSingleFileLimit(@TempDir Path directory) {
        Dataset<Row> dataset = dataset();
        FileOutputFormatOptions.GeoJson options = new FileOutputFormatOptions.GeoJson(
                "districts", "geom", "district_id", false);
        CanvasPreparedFileOutput output = preparedOutput(dataset, options);

        RunnerExecutionException failure = assertThrows(
                RunnerExecutionException.class,
                () -> GeoJsonFileOutputWriter.writeLocal(
                        directory, output, dataset, options, 100L)
        );

        assertEquals("GEOJSON_SIZE_LIMIT_EXCEEDED", failure.code());
    }

    @Test
    void writesEveryRfc7946GeometryKindAndNormalizesPolygonRingOrder(
            @TempDir Path directory
    ) throws Exception {
        Dataset<Row> dataset = geometryDataset(List.of(
                RowFactory.create(1L, "POINT (120 30)"),
                RowFactory.create(2L, "MULTIPOINT ((120 30), (121 31))"),
                RowFactory.create(3L, "LINESTRING (120 30, 121 31)"),
                RowFactory.create(4L, "MULTILINESTRING ((120 30, 121 31))"),
                RowFactory.create(5L, "POLYGON ((0 0, 0 1, 1 1, 1 0, 0 0))"),
                RowFactory.create(6L, "MULTIPOLYGON (((0 0, 0 1, 1 1, 1 0, 0 0)))"),
                RowFactory.create(7L, "GEOMETRYCOLLECTION (POINT (120 30), LINESTRING (120 30, 121 31))")
        ));
        FileOutputFormatOptions.GeoJson options = new FileOutputFormatOptions.GeoJson(
                "all_geometry_kinds", "geom", "district_id", false);
        CanvasPreparedFileOutput output = minimalPreparedOutput(
                dataset, options, GeometryKind.GEOMETRY, FileOutputConflictPolicy.FAIL_IF_EXISTS);

        Path artifact = GeoJsonFileOutputWriter.writeLocal(
                directory, output, dataset, options, GeoJsonFileOutputWriter.MAX_ARTIFACT_BYTES);
        JsonNode document = JsonSupport.strictObjectMapper().readTree(Files.readString(artifact));

        assertEquals(List.of(
                        "Point", "MultiPoint", "LineString", "MultiLineString",
                        "Polygon", "MultiPolygon", "GeometryCollection"),
                document.path("features").valueStream()
                        .map(value -> value.path("geometry").path("type").asText())
                        .toList());
        JsonNode exterior = feature(document, 5L)
                .path("geometry").path("coordinates").get(0);
        assertEquals(1d, exterior.get(1).get(0).asDouble());
        assertEquals(0d, exterior.get(1).get(1).asDouble());
        assertEquals("Point", feature(document, 7L).path("geometry")
                .path("geometries").get(0).path("type").asText());
    }

    @Test
    void writesEmptyUtf8FeatureCollectionWithoutLegacyMembersOrBom(@TempDir Path directory)
            throws Exception {
        Dataset<Row> dataset = geometryDataset(List.of()).limit(0);
        FileOutputFormatOptions.GeoJson options = new FileOutputFormatOptions.GeoJson(
                "empty", "geom", null, false);
        CanvasPreparedFileOutput output = minimalPreparedOutput(
                dataset, options, GeometryKind.POINT, FileOutputConflictPolicy.FAIL_IF_EXISTS);

        Path artifact = GeoJsonFileOutputWriter.writeLocal(
                directory, output, dataset, options, GeoJsonFileOutputWriter.MAX_ARTIFACT_BYTES);
        byte[] bytes = Files.readAllBytes(artifact);
        String json = new String(bytes, StandardCharsets.UTF_8);
        JsonNode document = JsonSupport.strictObjectMapper().readTree(json);

        assertEquals(0, document.path("features").size());
        assertFalse(document.has("crs"));
        assertFalse(document.has("bbox"));
        assertFalse(bytes.length >= 3
                && bytes[0] == (byte) 0xef && bytes[1] == (byte) 0xbb && bytes[2] == (byte) 0xbf);
    }

    @Test
    void rejectsRuntimeKindDriftEmptyOutOfRangeAndNonXyCoordinates(@TempDir Path directory)
            throws Exception {
        assertGeometryFailure(
                directory.resolve("kind"), "LINESTRING (120 30, 121 31)",
                GeometryKind.POINT, "GEOJSON_GEOMETRY_TYPE_MISMATCH");
        assertGeometryFailure(
                directory.resolve("empty"), "POINT EMPTY",
                GeometryKind.POINT, "GEOJSON_EMPTY_GEOMETRY_UNSUPPORTED");
        assertGeometryFailure(
                directory.resolve("range"), "POINT (181 30)",
                GeometryKind.POINT, "GEOJSON_COORDINATE_OUT_OF_RANGE");
        assertGeometryFailure(
                directory.resolve("xyz"), "POINT Z (120 30 5)",
                GeometryKind.POINT, "GEOJSON_COORDINATE_INVALID");
    }

    @Test
    void commitsThroughStagingAndProtectsExistingTargets(@TempDir Path directory)
            throws Exception {
        Dataset<Row> dataset = geometryDataset(List.of(RowFactory.create(1L, "POINT (120 30)")));
        FileOutputFormatOptions.GeoJson options = new FileOutputFormatOptions.GeoJson(
                "districts", "geom", null, false);
        Path local = directory.resolve("local");
        Files.createDirectories(local);
        CanvasPreparedFileOutput failIfExists = minimalPreparedOutput(
                dataset, options, GeometryKind.POINT, FileOutputConflictPolicy.FAIL_IF_EXISTS);
        Path artifact = GeoJsonFileOutputWriter.writeLocal(
                local, failIfExists, dataset, options, GeoJsonFileOutputWriter.MAX_ARTIFACT_BYTES);
        Configuration configuration = new Configuration(false);
        FileSystem fileSystem = FileSystem.getLocal(configuration);
        org.apache.hadoop.fs.Path staging = new org.apache.hadoop.fs.Path(
                directory.resolve("staging").toUri());
        org.apache.hadoop.fs.Path target = new org.apache.hadoop.fs.Path(
                directory.resolve("target").toUri());

        GeoJsonFileOutputWriter.uploadAndCommit(
                fileSystem, configuration, staging, target, artifact, failIfExists, options);
        assertTrue(Files.isRegularFile(directory.resolve("target/districts.geojson")));
        assertTrue(Files.isRegularFile(directory.resolve("target/_SUCCESS")));

        Files.writeString(directory.resolve("target/keep.txt"), "original");
        RunnerExecutionException conflict = assertThrows(
                RunnerExecutionException.class,
                () -> GeoJsonFileOutputWriter.uploadAndCommit(
                        fileSystem, configuration,
                        new org.apache.hadoop.fs.Path(directory.resolve("staging-conflict").toUri()),
                        target, artifact, failIfExists, options));
        assertEquals("FILE_OUTPUT_TARGET_EXISTS", conflict.code());
        assertEquals("original", Files.readString(directory.resolve("target/keep.txt")));

        CanvasPreparedFileOutput overwrite = minimalPreparedOutput(
                dataset, options, GeometryKind.POINT, FileOutputConflictPolicy.OVERWRITE);
        GeoJsonFileOutputWriter.uploadAndCommit(
                fileSystem, configuration,
                new org.apache.hadoop.fs.Path(directory.resolve("staging-overwrite").toUri()),
                target, artifact, overwrite, options);
        assertFalse(Files.exists(directory.resolve("target/keep.txt")));
        assertTrue(Files.isRegularFile(directory.resolve("target/districts.geojson")));
        assertTrue(Files.isRegularFile(directory.resolve("target/_SUCCESS")));
    }

    private void assertGeometryFailure(
            Path directory,
            String wkt,
            GeometryKind expectedKind,
            String expectedCode
    ) throws Exception {
        Files.createDirectories(directory);
        Dataset<Row> dataset = geometryDataset(List.of(RowFactory.create(1L, wkt)));
        FileOutputFormatOptions.GeoJson options = new FileOutputFormatOptions.GeoJson(
                "invalid", "geom", null, false);
        CanvasPreparedFileOutput output = minimalPreparedOutput(
                dataset, options, expectedKind, FileOutputConflictPolicy.FAIL_IF_EXISTS);

        RunnerExecutionException failure = assertThrows(
                RunnerExecutionException.class,
                () -> GeoJsonFileOutputWriter.writeLocal(
                        directory, output, dataset, options,
                        GeoJsonFileOutputWriter.MAX_ARTIFACT_BYTES));

        assertEquals(expectedCode, failure.code());
        assertFalse(failure.getMessage().contains(wkt));
    }

    private static JsonNode feature(JsonNode document, long id) {
        return document.path("features").valueStream()
                .filter(feature -> feature.path("id").asLong() == id)
                .findFirst()
                .orElseThrow();
    }

    private Dataset<Row> dataset() {
        List<CanvasColumnSchema> rawColumns = List.of(
                scalar("district_id", PlatformDataType.LONG, null, null, null, false),
                scalar("district_name", PlatformDataType.STRING, 20, null, null, false),
                scalar("active", PlatformDataType.BOOLEAN, null, null, null, true),
                scalar("amount", PlatformDataType.DECIMAL, null, 8, 2, true),
                scalar("business_date", PlatformDataType.DATE, null, null, null, true),
                scalar("geom_wkt", PlatformDataType.STRING, 128, null, null, true)
        );
        Dataset<Row> raw = spark.createDataFrame(List.of(
                RowFactory.create(
                        1L, "杭州", true, new BigDecimal("1234.50"),
                        Date.valueOf("2026-08-01"), "POINT (120.15 30.28)"),
                RowFactory.create(
                        2L, "宁波", null, null,
                        Date.valueOf("2026-08-02"), null)
        ), SparkTypeMapper.toStructType(rawColumns));
        Column geometry = expr("ST_SetSRID(ST_GeomFromWKT(geom_wkt), 4326)").alias("geom");
        return raw.select(
                col("district_id"),
                col("district_name"),
                col("active"),
                col("amount"),
                col("business_date"),
                geometry
        ).repartition(2);
    }

    private Dataset<Row> geometryDataset(List<Row> rows) {
        List<CanvasColumnSchema> rawColumns = List.of(
                scalar("district_id", PlatformDataType.LONG, null, null, null, false),
                scalar("geom_wkt", PlatformDataType.STRING, 512, null, null, true)
        );
        Dataset<Row> raw = spark.createDataFrame(rows, SparkTypeMapper.toStructType(rawColumns));
        return raw.select(
                col("district_id"),
                expr("ST_SetSRID(ST_GeomFromWKT(geom_wkt), 4326)").alias("geom")
        );
    }

    private static CanvasPreparedFileOutput minimalPreparedOutput(
            Dataset<Row> dataset,
            FileOutputFormatOptions.GeoJson options,
            GeometryKind kind,
            FileOutputConflictPolicy conflictPolicy
    ) {
        UUID dataSourceId = UUID.randomUUID();
        CanvasTableSchema schema = new CanvasTableSchema(
                "districts", null,
                List.of(
                        scalar("district_id", PlatformDataType.LONG, null, null, null, false),
                        new CanvasColumnSchema(
                                "geom", PlatformDataType.GEOMETRY, null, null, null,
                                true, null, false, false, "Geometry",
                                new GeometryTypeDefinition(
                                        kind, CrsReference.epsg(4326), CoordinateDimension.XY))
                ),
                CanvasDatasetKind.BOUNDED, null, null);
        RuntimeDataSource runtimeDataSource = new RuntimeDataSource(
                dataSourceId, ConnectionKind.S3, null,
                Set.of(DataSourcePurpose.DISTRIBUTION), null, null, List.of(), null,
                new RuntimeS3Connection(
                        "https://s3.example.invalid", "us-east-1", "exports", "root",
                        true, "not-used", "not-used"));
        FileOutputNodeDefinition node = new FileOutputNodeDefinition(
                UUID.randomUUID().toString(), "GeoJSON 输出",
                new CanvasNodeLayout(0d, 0d, 240d, 120d),
                new FileOutputConfiguration(
                        "districts", dataSourceId.toString(), "districts",
                        conflictPolicy, options));
        return new CanvasPreparedFileOutput(
                node, UUID.randomUUID().toString(), "districts", runtimeDataSource,
                "districts", "s3a://exports/root/districts", conflictPolicy, options,
                schema, dataset);
    }

    private static CanvasPreparedFileOutput preparedOutput(
            Dataset<Row> dataset,
            FileOutputFormatOptions.GeoJson options
    ) {
        UUID dataSourceId = UUID.randomUUID();
        CanvasTableSchema schema = new CanvasTableSchema(
                "districts",
                null,
                List.of(
                        scalar("district_id", PlatformDataType.LONG, null, null, null, false),
                        scalar("district_name", PlatformDataType.STRING, 20, null, null, false),
                        scalar("active", PlatformDataType.BOOLEAN, null, null, null, true),
                        scalar("amount", PlatformDataType.DECIMAL, null, 8, 2, true),
                        scalar("business_date", PlatformDataType.DATE, null, null, null, true),
                        new CanvasColumnSchema(
                                "geom", PlatformDataType.GEOMETRY, null, null, null,
                                true, null, false, false, "Geometry",
                                new GeometryTypeDefinition(
                                        GeometryKind.POINT,
                                        CrsReference.epsg(4326),
                                        CoordinateDimension.XY))
                ),
                CanvasDatasetKind.BOUNDED,
                null,
                null
        );
        RuntimeDataSource runtimeDataSource = new RuntimeDataSource(
                dataSourceId,
                ConnectionKind.S3,
                null,
                Set.of(DataSourcePurpose.DISTRIBUTION),
                null,
                null,
                List.of(),
                null,
                new RuntimeS3Connection(
                        "https://s3.example.invalid", "us-east-1", "exports", "root",
                        true, "not-used", "not-used")
        );
        FileOutputNodeDefinition node = new FileOutputNodeDefinition(
                UUID.randomUUID().toString(),
                "GeoJSON 输出",
                new CanvasNodeLayout(0d, 0d, 240d, 120d),
                new FileOutputConfiguration(
                        "districts", dataSourceId.toString(), "districts",
                        FileOutputConflictPolicy.FAIL_IF_EXISTS, options)
        );
        return new CanvasPreparedFileOutput(
                node, UUID.randomUUID().toString(), "districts", runtimeDataSource,
                "districts", "s3a://exports/root/districts",
                FileOutputConflictPolicy.FAIL_IF_EXISTS, options, schema, dataset);
    }

    private static CanvasColumnSchema scalar(
            String name,
            PlatformDataType type,
            Integer length,
            Integer precision,
            Integer scale,
            boolean nullable
    ) {
        return new CanvasColumnSchema(
                name, type, length, precision, scale, nullable,
                null, false, false, null);
    }
}
