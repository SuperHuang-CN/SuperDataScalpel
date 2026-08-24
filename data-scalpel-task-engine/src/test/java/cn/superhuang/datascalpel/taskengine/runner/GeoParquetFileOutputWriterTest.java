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
import cn.superhuang.data.scalpel.contract.task.GeoParquetCompressionCodec;
import cn.superhuang.data.scalpel.contract.task.GeoParquetCoveringMode;
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
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.hadoop.util.HadoopInputFile;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.Geometry;

import java.nio.file.Path;
import java.nio.file.Files;
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
class GeoParquetFileOutputWriterTest {
    private SparkSession spark;

    @BeforeAll
    void startSpark() {
        spark = SedonaSparkSupport.initialize(SedonaSparkSupport.builder()
                .master("local[2]")
                .appName("geoparquet-file-output-writer-test")
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
    void writesGeoParquetElevenWithExplicitCrsAndRowBbox(@TempDir Path directory) throws Exception {
        Dataset<Row> dataset = dataset();
        FileOutputFormatOptions.GeoParquet options = new FileOutputFormatOptions.GeoParquet(
                "geom", GeoParquetCompressionCodec.SNAPPY, GeoParquetCoveringMode.ROW_BBOX);
        Path target = directory.resolve("districts-geoparquet");
        CanvasPreparedFileOutput output = preparedOutput(dataset, options, target.toUri().toString());

        GeoParquetFileOutputWriter.write(output, dataset);

        Dataset<Row> restored = spark.read().format("geoparquet").load(target.toUri().toString());
        assertEquals(2L, restored.count());
        assertTrue(List.of(restored.schema().fieldNames()).contains("geom_bbox"));
        Geometry geometry = restored.filter(col("district_id").equalTo(1L))
                .select("geom").first().getAs(0);
        assertEquals("Point", geometry.getGeometryType());
        assertEquals(4326, geometry.getSRID());

        boolean sawPointMetadata = false;
        for (Path part : partFiles(target)) {
            try (ParquetFileReader reader = ParquetFileReader.open(HadoopInputFile.fromPath(
                    new org.apache.hadoop.fs.Path(part.toUri()),
                    spark.sparkContext().hadoopConfiguration()))) {
                String geoMetadata = reader.getFooter().getFileMetaData()
                        .getKeyValueMetaData().get("geo");
                JsonNode geo = JsonSupport.strictObjectMapper().readTree(geoMetadata);
                assertEquals("1.1.0", geo.path("version").asText());
                assertEquals("geom", geo.path("primary_column").asText());
                assertEquals("WKB", geo.path("columns").path("geom").path("encoding").asText());
                JsonNode geometryMetadata = geo.path("columns").path("geom");
                assertEquals("EPSG", geometryMetadata.path("crs")
                        .path("id").path("authority").asText());
                assertEquals(4326, geometryMetadata.path("crs")
                        .path("id").path("code").asInt());
                boolean partContainsPoint = geometryMetadata.path("geometry_types").valueStream()
                        .anyMatch(type -> type.asText().equals("Point"));
                sawPointMetadata |= partContainsPoint;
                if (partContainsPoint) {
                    assertEquals(4, geometryMetadata.path("bbox").size());
                }
                assertEquals(List.of("geom_bbox", "xmin"),
                        geometryMetadata.path("covering").path("bbox").path("xmin")
                                .valueStream().map(JsonNode::asText).toList());
            }
        }
        assertTrue(sawPointMetadata);
        assertTrue(Files.isRegularFile(target.resolve("_SUCCESS")));
        assertFalse(Files.exists(target.resolve("_metadata")));
        assertFalse(Files.exists(target.resolve("_common_metadata")));
    }

    @Test
    void writesZstdWithoutRowBbox(@TempDir Path directory) throws Exception {
        Dataset<Row> dataset = dataset();
        FileOutputFormatOptions.GeoParquet options = new FileOutputFormatOptions.GeoParquet(
                "geom", GeoParquetCompressionCodec.ZSTD, GeoParquetCoveringMode.NONE);
        Path target = directory.resolve("districts-geoparquet-zstd");
        CanvasPreparedFileOutput output = preparedOutput(dataset, options, target.toUri().toString());

        GeoParquetFileOutputWriter.write(output, dataset);

        Dataset<Row> restored = spark.read().format("geoparquet").load(target.toUri().toString());
        assertEquals(2L, restored.count());
        assertFalse(List.of(restored.schema().fieldNames()).contains("geom_bbox"));
        for (Path part : partFiles(target)) {
            try (ParquetFileReader reader = ParquetFileReader.open(HadoopInputFile.fromPath(
                    new org.apache.hadoop.fs.Path(part.toUri()),
                    spark.sparkContext().hadoopConfiguration()))) {
                reader.getRowGroups().stream()
                        .flatMap(block -> block.getColumns().stream())
                        .forEach(column -> assertEquals("ZSTD", column.getCodec().name()));
                String geoMetadata = reader.getFooter().getFileMetaData()
                        .getKeyValueMetaData().get("geo");
                JsonNode geo = JsonSupport.strictObjectMapper().readTree(geoMetadata);
                assertTrue(geo.path("columns").path("geom").path("covering").isMissingNode());
            }
        }
    }

    @Test
    void generatesProjJsonForCgcs2000WithoutRemoteCrsLookup() throws Exception {
        JsonNode projJson = JsonSupport.strictObjectMapper()
                .readTree(cn.superhuang.datascalpel.taskengine.canvas.GeoParquetCrsSupport
                        .projJson(4490));

        assertFalse(projJson.isMissingNode());
        assertEquals("EPSG", projJson.path("id").path("authority").asText());
        assertEquals(4490, projJson.path("id").path("code").asInt());
    }

    @Test
    void writesGenericGeometryWithMixedKinds(@TempDir Path directory) throws Exception {
        Dataset<Row> dataset = geometryDataset(List.of(
                RowFactory.create(1L, "POINT (120 30)"),
                RowFactory.create(2L, "LINESTRING (120 30, 121 31)")
        ));
        FileOutputFormatOptions.GeoParquet options = new FileOutputFormatOptions.GeoParquet(
                "geom", GeoParquetCompressionCodec.SNAPPY, GeoParquetCoveringMode.NONE);
        Path target = directory.resolve("mixed-geoparquet");
        CanvasPreparedFileOutput output = preparedOutput(
                dataset, options, target.toUri().toString(), GeometryKind.GEOMETRY,
                FileOutputConflictPolicy.FAIL_IF_EXISTS);

        GeoParquetFileOutputWriter.write(output, dataset);

        Dataset<Row> restored = spark.read().format("geoparquet").load(target.toUri().toString());
        assertEquals(2L, restored.count());
        java.util.Set<String> restoredKinds = new java.util.HashSet<>();
        for (Row row : restored.select("geom").collectAsList()) {
            restoredKinds.add(((Geometry) row.get(0)).getGeometryType());
        }
        assertEquals(Set.of("Point", "LineString"), restoredKinds);
    }

    @Test
    void rejectsRuntimeKindDriftEmptyNonFiniteAndNonXyGeometry(@TempDir Path directory)
            throws Exception {
        assertGeometryFailure(
                directory.resolve("kind"), "LINESTRING (120 30, 121 31)",
                "GEOPARQUET_GEOMETRY_TYPE_MISMATCH");
        assertGeometryFailure(
                directory.resolve("empty"), "POINT EMPTY",
                "GEOPARQUET_EMPTY_GEOMETRY_UNSUPPORTED");
        assertGeometryFailure(
                directory.resolve("xyz"), "POINT Z (120 30 5)",
                "GEOPARQUET_COORDINATE_INVALID");
        assertGeometryFailure(
                directory.resolve("nonfinite"), "POINT (NaN 30)",
                "GEOPARQUET_COORDINATE_INVALID");
    }

    @Test
    void honorsFailIfExistsAndOverwriteSaveModes(@TempDir Path directory) {
        Dataset<Row> dataset = geometryDataset(List.of(RowFactory.create(1L, "POINT (120 30)")));
        FileOutputFormatOptions.GeoParquet options = new FileOutputFormatOptions.GeoParquet(
                "geom", GeoParquetCompressionCodec.SNAPPY, GeoParquetCoveringMode.NONE);
        Path target = directory.resolve("conflict-geoparquet");
        CanvasPreparedFileOutput failIfExists = preparedOutput(
                dataset, options, target.toUri().toString(), GeometryKind.POINT,
                FileOutputConflictPolicy.FAIL_IF_EXISTS);

        GeoParquetFileOutputWriter.write(failIfExists, dataset);
        RunnerExecutionException conflict = assertThrows(
                RunnerExecutionException.class,
                () -> GeoParquetFileOutputWriter.write(failIfExists, dataset));
        assertEquals("GEOPARQUET_WRITE_FAILED", conflict.code());
        assertTrue(Files.isRegularFile(target.resolve("_SUCCESS")));

        CanvasPreparedFileOutput overwrite = preparedOutput(
                dataset, options, target.toUri().toString(), GeometryKind.POINT,
                FileOutputConflictPolicy.OVERWRITE);
        GeoParquetFileOutputWriter.write(overwrite, dataset);
        assertTrue(Files.isRegularFile(target.resolve("_SUCCESS")));
    }

    private void assertGeometryFailure(Path target, String wkt, String expectedCode) {
        Dataset<Row> dataset = geometryDataset(List.of(RowFactory.create(1L, wkt)));
        FileOutputFormatOptions.GeoParquet options = new FileOutputFormatOptions.GeoParquet(
                "geom", GeoParquetCompressionCodec.SNAPPY, GeoParquetCoveringMode.NONE);
        CanvasPreparedFileOutput output = preparedOutput(
                dataset, options, target.toUri().toString(), GeometryKind.POINT,
                FileOutputConflictPolicy.FAIL_IF_EXISTS);

        RunnerExecutionException failure = assertThrows(
                RunnerExecutionException.class,
                () -> GeoParquetFileOutputWriter.write(output, dataset));

        assertEquals(expectedCode, failure.code());
        assertFalse(failure.getMessage().contains(wkt));
    }

    private static List<Path> partFiles(Path target) throws Exception {
        try (var files = Files.list(target)) {
            return files.filter(path -> path.getFileName().toString().startsWith("part-"))
                    .toList();
        }
    }

    private Dataset<Row> dataset() {
        List<CanvasColumnSchema> rawColumns = List.of(
                scalar("district_id", PlatformDataType.LONG, false),
                scalar("district_name", PlatformDataType.STRING, false),
                scalar("geom_wkt", PlatformDataType.STRING, true)
        );
        Dataset<Row> raw = spark.createDataFrame(List.of(
                RowFactory.create(1L, "杭州", "POINT (120.15 30.28)"),
                RowFactory.create(2L, "宁波", null)
        ), SparkTypeMapper.toStructType(rawColumns));
        return raw.select(
                col("district_id"),
                col("district_name"),
                expr("ST_SetSRID(ST_GeomFromWKT(geom_wkt), 4326)").alias("geom")
        ).repartition(2);
    }

    private Dataset<Row> geometryDataset(List<Row> rows) {
        List<CanvasColumnSchema> rawColumns = List.of(
                scalar("district_id", PlatformDataType.LONG, false),
                scalar("geom_wkt", PlatformDataType.STRING, true)
        );
        Dataset<Row> raw = spark.createDataFrame(rows, SparkTypeMapper.toStructType(rawColumns));
        return raw.select(
                col("district_id"),
                expr("ST_SetSRID(ST_GeomFromWKT(geom_wkt), 4326)").alias("geom")
        ).repartition(2);
    }

    private static CanvasPreparedFileOutput preparedOutput(
            Dataset<Row> dataset,
            FileOutputFormatOptions.GeoParquet options,
            String targetUri
    ) {
        return preparedOutput(
                dataset, options, targetUri, GeometryKind.POINT,
                FileOutputConflictPolicy.FAIL_IF_EXISTS);
    }

    private static CanvasPreparedFileOutput preparedOutput(
            Dataset<Row> dataset,
            FileOutputFormatOptions.GeoParquet options,
            String targetUri,
            GeometryKind geometryKind,
            FileOutputConflictPolicy conflictPolicy
    ) {
        UUID dataSourceId = UUID.randomUUID();
        CanvasTableSchema schema = new CanvasTableSchema(
                "districts",
                null,
                List.of(
                        scalar("district_id", PlatformDataType.LONG, false),
                        scalar("district_name", PlatformDataType.STRING, false),
                        new CanvasColumnSchema(
                                "geom", PlatformDataType.GEOMETRY, null, null, null,
                                true, null, false, false, "Geometry",
                                new GeometryTypeDefinition(
                                        geometryKind,
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
                "GeoParquet 输出",
                new CanvasNodeLayout(0d, 0d, 240d, 120d),
                new FileOutputConfiguration(
                        "districts", dataSourceId.toString(), "districts",
                        conflictPolicy, options)
        );
        return new CanvasPreparedFileOutput(
                node, UUID.randomUUID().toString(), "districts", runtimeDataSource,
                "districts", targetUri, conflictPolicy, options, schema, dataset);
    }

    private static CanvasColumnSchema scalar(
            String name,
            PlatformDataType type,
            boolean nullable
    ) {
        return new CanvasColumnSchema(
                name, type, type == PlatformDataType.STRING ? 64 : null,
                null, null, nullable, null, false, false, null);
    }
}
