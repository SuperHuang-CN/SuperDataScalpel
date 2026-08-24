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
import cn.superhuang.data.scalpel.contract.task.ShapefileAttributeMapping;
import cn.superhuang.data.scalpel.contract.task.ShapefilePackageMode;
import cn.superhuang.data.scalpel.contract.task.ShapefileShapeType;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasPreparedFileOutput;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeDataSource;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeS3Connection;
import cn.superhuang.datascalpel.taskengine.spark.SedonaSparkSupport;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.geotools.api.data.Query;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.feature.FeatureIterator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipFile;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.expr;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ShapefileFileOutputWriterTest {
    private SparkSession spark;

    @BeforeAll
    void startSpark() {
        spark = SedonaSparkSupport.initialize(SedonaSparkSupport.builder()
                .master("local[2]")
                .appName("shapefile-file-output-writer-test")
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
    void writesFiveComponentsWithStrictDbfSchemaAndReadsThemBack(@TempDir Path directory)
            throws Exception {
        Dataset<Row> dataset = dataset();
        FileOutputFormatOptions.Shapefile options = options(
                "districts", ShapefilePackageMode.COMPONENT_DIRECTORY, 40);
        CanvasPreparedFileOutput output = preparedOutput(dataset, options);

        List<Path> artifacts = ShapefileFileOutputWriter.writeLocal(
                directory, output, dataset, options);

        assertEquals(
                List.of(
                        "districts.shp",
                        "districts.shx",
                        "districts.dbf",
                        "districts.prj",
                        "districts.cpg"
                ),
                artifacts.stream().map(path -> path.getFileName().toString()).toList()
        );
        assertTrue(artifacts.stream().allMatch(Files::isRegularFile));
        assertEquals("UTF-8", Files.readString(directory.resolve("districts.cpg")).trim());
        assertTrue(Files.readString(directory.resolve("districts.prj")).contains("WGS"));

        ShapefileDataStore store = new ShapefileDataStore(
                directory.resolve("districts.shp").toUri().toURL());
        store.setCharset(StandardCharsets.UTF_8);
        try {
            List<String> dbfFields = store.getSchema().getAttributeDescriptors().stream()
                    .filter(descriptor -> !Geometry.class.isAssignableFrom(descriptor.getType().getBinding()))
                    .map(descriptor -> descriptor.getLocalName().toUpperCase())
                    .toList();
            assertEquals(List.of("DIST_ID", "DIST_NAME", "ACTIVE", "AMOUNT", "BIZ_DATE"), dbfFields);
            assertEquals(2, store.getFeatureSource().getCount(Query.ALL));

            try (FeatureIterator<SimpleFeature> features = store.getFeatureSource()
                    .getFeatures().features()) {
                SimpleFeature first = features.next();
                assertInstanceOf(Point.class, first.getDefaultGeometry());
                assertEquals("杭州", first.getAttribute("DIST_NAME"));
                assertEquals(Boolean.TRUE, first.getAttribute("ACTIVE"));
                assertEquals(0, new BigDecimal("1234.50").compareTo(new BigDecimal(
                        first.getAttribute("AMOUNT").toString())));

                SimpleFeature second = features.next();
                assertNull(second.getDefaultGeometry());
                assertEquals("宁波", second.getAttribute("DIST_NAME"));
                assertFalse(features.hasNext());
            }
        } finally {
            store.dispose();
        }
    }

    @Test
    void writesZipWithComponentsAtArchiveRoot(@TempDir Path directory) throws Exception {
        Dataset<Row> dataset = dataset();
        FileOutputFormatOptions.Shapefile options = options(
                "districts", ShapefilePackageMode.ZIP, 40);
        CanvasPreparedFileOutput output = preparedOutput(dataset, options);

        List<Path> artifacts = ShapefileFileOutputWriter.writeLocal(
                directory, output, dataset, options);

        assertEquals(List.of("districts.zip"),
                artifacts.stream().map(path -> path.getFileName().toString()).toList());
        try (ZipFile zip = new ZipFile(artifacts.getFirst().toFile(), StandardCharsets.UTF_8)) {
            Set<String> entries = new HashSet<>();
            zip.stream().forEach(entry -> entries.add(entry.getName()));
            assertEquals(Set.of(
                    "districts.shp",
                    "districts.shx",
                    "districts.dbf",
                    "districts.prj",
                    "districts.cpg"
            ), entries);
            assertTrue(entries.stream().noneMatch(name -> name.contains("/")));
        }
    }

    @Test
    void protectsExistingTargetsAndCommitsSuccessMarkerLast(@TempDir Path directory)
            throws Exception {
        Path artifactDirectory = Files.createDirectory(directory.resolve("artifacts"));
        Dataset<Row> dataset = dataset();
        FileOutputFormatOptions.Shapefile options = options(
                "districts", ShapefilePackageMode.COMPONENT_DIRECTORY, 40);
        CanvasPreparedFileOutput failOutput = preparedOutput(
                dataset, options, FileOutputConflictPolicy.FAIL_IF_EXISTS);
        List<Path> artifacts = ShapefileFileOutputWriter.writeLocal(
                artifactDirectory, failOutput, dataset, options);
        Configuration hadoop = new Configuration(false);

        try (FileSystem fileSystem = FileSystem.newInstance(URI.create("file:///"), hadoop)) {
            Path existingTarget = Files.createDirectory(directory.resolve("existing-target"));
            Files.writeString(existingTarget.resolve("original.txt"), "keep");
            RunnerExecutionException conflict = assertThrows(
                    RunnerExecutionException.class,
                    () -> ShapefileFileOutputWriter.uploadAndCommit(
                            fileSystem,
                            hadoop,
                            new org.apache.hadoop.fs.Path(directory.resolve("staging-fail").toUri()),
                            new org.apache.hadoop.fs.Path(existingTarget.toUri()),
                            artifacts,
                            failOutput,
                            options)
            );
            assertEquals("FILE_OUTPUT_TARGET_EXISTS", conflict.code());
            assertEquals("keep", Files.readString(existingTarget.resolve("original.txt")));
            assertFalse(Files.exists(existingTarget.resolve("_SUCCESS")));

            Path overwriteTarget = Files.createDirectory(directory.resolve("overwrite-target"));
            Files.writeString(overwriteTarget.resolve("old.txt"), "remove");
            CanvasPreparedFileOutput overwriteOutput = preparedOutput(
                    dataset, options, FileOutputConflictPolicy.OVERWRITE);
            ShapefileFileOutputWriter.uploadAndCommit(
                    fileSystem,
                    hadoop,
                    new org.apache.hadoop.fs.Path(directory.resolve("staging-overwrite").toUri()),
                    new org.apache.hadoop.fs.Path(overwriteTarget.toUri()),
                    artifacts,
                    overwriteOutput,
                    options
            );

            assertFalse(Files.exists(overwriteTarget.resolve("old.txt")));
            assertTrue(Files.isRegularFile(overwriteTarget.resolve("districts.shp")));
            assertTrue(Files.isRegularFile(overwriteTarget.resolve("districts.dbf")));
            assertEquals(0L, Files.size(overwriteTarget.resolve("_SUCCESS")));
        }
    }

    @Test
    void failsBeforeReturningAnArtifactWhenConfiguredSizeLimitIsReached(@TempDir Path directory) {
        Dataset<Row> dataset = dataset();
        FileOutputFormatOptions.Shapefile options = options(
                "districts", ShapefilePackageMode.COMPONENT_DIRECTORY, 40);
        CanvasPreparedFileOutput output = preparedOutput(dataset, options);

        RunnerExecutionException failure = assertThrows(
                RunnerExecutionException.class,
                () -> ShapefileFileOutputWriter.writeLocal(
                        directory, output, dataset, options, 100L)
        );

        assertEquals("SHAPEFILE_SIZE_LIMIT_EXCEEDED", failure.code());
    }

    @Test
    void rejectsUtf8TextThatExceedsTheConfiguredDbfByteWidth(@TempDir Path directory) {
        Dataset<Row> dataset = dataset();
        FileOutputFormatOptions.Shapefile options = options(
                "districts", ShapefilePackageMode.COMPONENT_DIRECTORY, 4);
        CanvasPreparedFileOutput output = preparedOutput(dataset, options);

        RunnerExecutionException failure = assertThrows(
                RunnerExecutionException.class,
                () -> ShapefileFileOutputWriter.writeLocal(
                        directory, output, dataset, options)
        );

        assertEquals("SHAPEFILE_ATTRIBUTE_VALUE_TOO_LONG", failure.code());
        assertFalse(failure.getMessage().contains("杭州"));
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

    private static FileOutputFormatOptions.Shapefile options(
            String baseName,
            ShapefilePackageMode packageMode,
            int stringWidth
    ) {
        return new FileOutputFormatOptions.Shapefile(
                baseName,
                packageMode,
                "geom",
                ShapefileShapeType.POINT,
                List.of(
                        new ShapefileAttributeMapping("district_id", "DIST_ID", null),
                        new ShapefileAttributeMapping("district_name", "DIST_NAME", stringWidth),
                        new ShapefileAttributeMapping("active", "ACTIVE", null),
                        new ShapefileAttributeMapping("amount", "AMOUNT", null),
                        new ShapefileAttributeMapping("business_date", "BIZ_DATE", null)
                )
        );
    }

    private static CanvasPreparedFileOutput preparedOutput(
            Dataset<Row> dataset,
            FileOutputFormatOptions.Shapefile options
    ) {
        return preparedOutput(dataset, options, FileOutputConflictPolicy.FAIL_IF_EXISTS);
    }

    private static CanvasPreparedFileOutput preparedOutput(
            Dataset<Row> dataset,
            FileOutputFormatOptions.Shapefile options,
            FileOutputConflictPolicy conflictPolicy
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
                                "geom",
                                PlatformDataType.GEOMETRY,
                                null,
                                null,
                                null,
                                true,
                                null,
                                false,
                                false,
                                "Geometry",
                                new GeometryTypeDefinition(
                                        GeometryKind.POINT,
                                        CrsReference.epsg(4326),
                                        CoordinateDimension.XY)
                        )
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
                        "https://s3.example.invalid",
                        "us-east-1",
                        "exports",
                        "root",
                        true,
                        "not-used",
                        "not-used")
        );
        FileOutputNodeDefinition node = new FileOutputNodeDefinition(
                UUID.randomUUID().toString(),
                "Shapefile 输出",
                new CanvasNodeLayout(0d, 0d, 240d, 120d),
                new FileOutputConfiguration(
                        "districts",
                        dataSourceId.toString(),
                        "districts",
                        conflictPolicy,
                        options)
        );
        return new CanvasPreparedFileOutput(
                node,
                UUID.randomUUID().toString(),
                "districts",
                runtimeDataSource,
                "districts",
                "s3a://exports/root/districts",
                conflictPolicy,
                options,
                schema,
                dataset
        );
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
                name,
                type,
                length,
                precision,
                scale,
                nullable,
                null,
                false,
                false,
                null
        );
    }
}
