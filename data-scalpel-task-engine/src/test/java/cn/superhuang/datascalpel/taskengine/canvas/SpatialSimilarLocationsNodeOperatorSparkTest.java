package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.*;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.datascalpel.taskengine.compiler.lineage.CatalystLineageAnalyzer;
import cn.superhuang.datascalpel.taskengine.compiler.lineage.CatalystLineageMetadata;
import cn.superhuang.datascalpel.taskengine.compiler.lineage.CatalystLineageOutputCandidate;
import cn.superhuang.datascalpel.taskengine.spark.SedonaSparkSupport;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.sedona_sql.expressions.st_constructors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SpatialSimilarLocationsNodeOperatorSparkTest {
    private SparkSession spark;

    @BeforeAll
    void startSpark() {
        spark = SedonaSparkSupport.initialize(SedonaSparkSupport.builder()
                .master("local[1]").appName("spatial-similar-locations-test")
                .config("spark.ui.enabled", "false")
                .config("spark.driver.host", "127.0.0.1")
                .config("spark.driver.bindAddress", "127.0.0.1")
                .config("spark.sql.shuffle.partitions", "1").getOrCreate());
    }

    @AfterAll
    void stopSpark() {
        if (spark != null) spark.stop();
    }

    @Test
    void averagesReferencesUsesCombinedPopulationAndKeepsBothEndsDisjoint() {
        SparkCanvasTable references = geometryTable("references_raw", "references", List.of(
                        stringColumn("id"), stringColumn("status"), doubleColumn("a"),
                        doubleColumn("b"), stringColumn("wkt")),
                List.of(
                        RowFactory.create("r1", "active", 10d, 10d, "POINT (0 0)"),
                        RowFactory.create("r2", "active", 14d, 14d, "POINT (1 1)"),
                        RowFactory.create("ignored", "inactive", 100d, 100d, "POINT (2 2)")));
        SparkCanvasTable candidates = geometryTable("candidates_raw", "candidates", List.of(
                        stringColumn("id"), stringColumn("status"), stringColumn("name"),
                        doubleColumn("a"), doubleColumn("b"), stringColumn("wkt")),
                List.of(
                        RowFactory.create("c1", "active", "exact", 12d, 12d, "POINT (3 3)"),
                        RowFactory.create("c2", "active", "far", 20d, 20d, "POINT (4 4)"),
                        RowFactory.create("c3", "active", "middle", 8d, 8d, "POINT (5 5)"),
                        RowFactory.create("ignored", "inactive", "filtered", 12d, 12d, "POINT (6 6)")));
        SpatialSimilarLocationsConfiguration configuration = configuration(
                SpatialSimilarLocationsMatchMethod.ATTRIBUTE_VALUES,
                SpatialSimilarLocationsResultMode.BOTH,
                List.of(new SpatialSimilarLocationsAnalysisField("a", "analysis_a"),
                        new SpatialSimilarLocationsAnalysisField("b", "analysis_b")),
                List.of(new SpatialSimilarLocationsAppendField("name", "candidate_name")),
                activeFilter(), activeFilter());
        Map<String, SparkCanvasTable> inputs = new LinkedHashMap<>();
        inputs.put("references", references);
        inputs.put("candidates", candidates);
        RecordingIssueSink issues = new RecordingIssueSink();

        CanvasNodeOperationResult result = operator(configuration, inputs, issues);

        assertFalse(issues.hasErrors(), issues::toString);
        assertEquals(List.of("references", "candidates", "similar_locations"),
                new ArrayList<>(result.propagatedTables().keySet()));
        SparkCanvasTable output = result.propagatedTables().get("similar_locations");
        List<Row> rows = output.dataset().collectAsList();
        assertEquals(4, rows.size());
        List<Row> candidateRows = rows.stream()
                .filter(row -> "CANDIDATE".equals(row.getAs("location_type"))).toList();
        assertEquals(List.of("c1", "c2"), candidateRows.stream()
                .map(row -> (String) row.getAs("search_id")).sorted().toList());
        Row exact = candidateRows.stream().filter(row -> "c1".equals(row.getAs("search_id"))).findFirst().orElseThrow();
        assertEquals(0d, ((Number) exact.getAs("simindex")).doubleValue(), 1e-12);
        assertEquals("exact", exact.getAs("candidate_name"));
        Row far = candidateRows.stream().filter(row -> "c2".equals(row.getAs("search_id"))).findFirst().orElseThrow();
        assertEquals(128d / 16.96d, ((Number) far.getAs("simindex")).doubleValue(), 1e-9);
        assertEquals(-1, ((Number) far.getAs("dissimilarity_rank")).intValue());
        assertEquals(List.of("geometry", "location_type", "reference_id", "search_id",
                        "analysis_a", "analysis_b", "candidate_name", "similarity_rank",
                        "dissimilarity_rank", "simindex", "cosimindex", "label_rank"),
                output.schema().columns().stream().map(CanvasColumnSchema::name).toList());
    }

    @Test
    void profileSimilarityUsesCosineDifferenceWhereZeroIsMostSimilar() {
        SparkCanvasTable references = geometryTable("profile_reference_raw", "profile_reference", List.of(
                        stringColumn("id"), doubleColumn("a"), doubleColumn("b"), stringColumn("wkt")),
                List.of(RowFactory.create("r1", 10d, 30d, "POINT (0 0)"),
                        RowFactory.create("r2", 14d, 34d, "POINT (1 1)")));
        SparkCanvasTable candidates = geometryTable("profile_candidate_raw", "profile_candidate", List.of(
                        stringColumn("id"), doubleColumn("a"), doubleColumn("b"), stringColumn("wkt")),
                List.of(RowFactory.create("same", 12d, 32d, "POINT (2 2)"),
                        RowFactory.create("different", 30d, 10d, "POINT (3 3)")));
        SpatialSimilarLocationsConfiguration configuration = configuration(
                SpatialSimilarLocationsMatchMethod.ATTRIBUTE_PROFILES,
                SpatialSimilarLocationsResultMode.MOST_SIMILAR,
                List.of(new SpatialSimilarLocationsAnalysisField("a", "a"),
                        new SpatialSimilarLocationsAnalysisField("b", "b")),
                List.of(), null, null);
        configuration = new SpatialSimilarLocationsConfiguration(
                "profile_reference", "id", "shape", null,
                "profile_candidate", "id", "shape", null,
                configuration.analysisFields(), configuration.appendFields(), configuration.matchMethod(),
                configuration.resultMode(), configuration.numberOfResults(), configuration.outputTableName(),
                configuration.outputGeometryColumnName(), configuration.locationTypeColumnName(),
                configuration.similarityRankColumnName(), configuration.dissimilarityRankColumnName(),
                configuration.similarityIndexColumnName(), configuration.cosineIndexColumnName(),
                configuration.labelRankColumnName(), configuration.referenceIdOutputColumnName(),
                configuration.searchIdOutputColumnName());
        RecordingIssueSink issues = new RecordingIssueSink();

        CanvasNodeOperationResult result = operator(configuration,
                Map.of("profile_reference", references, "profile_candidate", candidates), issues);

        assertFalse(issues.hasErrors(), issues::toString);
        Row candidate = result.propagatedTables().get("similar_locations").dataset()
                .filter("location_type = 'CANDIDATE'").head();
        assertEquals("same", candidate.getAs("search_id"));
        assertEquals(0d, ((Number) candidate.getAs("cosimindex")).doubleValue(), 1e-12);
    }

    @Test
    void rejectsDuplicateCandidateIdsAtRuntime() {
        SparkCanvasTable references = geometryTable("invalid_reference_raw", "invalid_reference", List.of(
                        stringColumn("id"), doubleColumn("a"), stringColumn("wkt")),
                List.of(RowFactory.create("r1", 10d, "POINT (0 0)")));
        SparkCanvasTable candidates = geometryTable("invalid_candidate_raw", "invalid_candidate", List.of(
                        stringColumn("id"), doubleColumn("a"), stringColumn("wkt")),
                List.of(RowFactory.create("duplicate", 12d, "POINT (1 1)"),
                        RowFactory.create("duplicate", 14d, "POINT (2 2)")));
        SpatialSimilarLocationsConfiguration base = configuration(
                SpatialSimilarLocationsMatchMethod.ATTRIBUTE_VALUES,
                SpatialSimilarLocationsResultMode.MOST_SIMILAR,
                List.of(new SpatialSimilarLocationsAnalysisField("a", "a")), List.of(), null, null);
        SpatialSimilarLocationsConfiguration configuration = new SpatialSimilarLocationsConfiguration(
                "invalid_reference", "id", "shape", null,
                "invalid_candidate", "id", "shape", null,
                base.analysisFields(), base.appendFields(), base.matchMethod(), base.resultMode(),
                base.numberOfResults(), base.outputTableName(), base.outputGeometryColumnName(),
                base.locationTypeColumnName(), base.similarityRankColumnName(), base.dissimilarityRankColumnName(),
                base.similarityIndexColumnName(), base.cosineIndexColumnName(), base.labelRankColumnName(),
                base.referenceIdOutputColumnName(), base.searchIdOutputColumnName());
        RecordingIssueSink issues = new RecordingIssueSink();
        CanvasNodeOperationResult result = operator(configuration,
                Map.of("invalid_reference", references, "invalid_candidate", candidates), issues);

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> result.propagatedTables().get("similar_locations").dataset().collectAsList());
        assertTrue(messageChain(failure).contains("SPATIAL_SIMILAR_LOCATIONS_CANDIDATE_ID_DUPLICATE"),
                () -> messageChain(failure));
    }

    @Test
    void leastSimilarUsesNegativeLabelsWhenRequestedCountIncludesEveryCandidate() {
        SparkCanvasTable references = geometryTable("least_reference_raw", "least_reference", List.of(
                        stringColumn("id"), doubleColumn("a"), stringColumn("wkt")),
                List.of(RowFactory.create("r1", 0d, "POINT (0 0)")));
        SparkCanvasTable candidates = geometryTable("least_candidate_raw", "least_candidate", List.of(
                        stringColumn("id"), doubleColumn("a"), stringColumn("wkt")),
                List.of(RowFactory.create("near", 1d, "POINT (1 1)"),
                        RowFactory.create("far", 10d, "POINT (2 2)")));
        SpatialSimilarLocationsConfiguration base = configuration(
                SpatialSimilarLocationsMatchMethod.ATTRIBUTE_VALUES,
                SpatialSimilarLocationsResultMode.LEAST_SIMILAR,
                List.of(new SpatialSimilarLocationsAnalysisField("a", "a")), List.of(), null, null);
        SpatialSimilarLocationsConfiguration configuration = new SpatialSimilarLocationsConfiguration(
                "least_reference", "id", "shape", null,
                "least_candidate", "id", "shape", null,
                base.analysisFields(), base.appendFields(), base.matchMethod(), base.resultMode(),
                10, base.outputTableName(), base.outputGeometryColumnName(), base.locationTypeColumnName(),
                base.similarityRankColumnName(), base.dissimilarityRankColumnName(),
                base.similarityIndexColumnName(), base.cosineIndexColumnName(), base.labelRankColumnName(),
                base.referenceIdOutputColumnName(), base.searchIdOutputColumnName());
        RecordingIssueSink issues = new RecordingIssueSink();

        CanvasNodeOperationResult result = operator(configuration,
                Map.of("least_reference", references, "least_candidate", candidates), issues);

        assertFalse(issues.hasErrors(), issues::toString);
        List<Row> candidateRows = result.propagatedTables().get("similar_locations").dataset()
                .filter("location_type = 'CANDIDATE'").collectAsList();
        assertEquals(2, candidateRows.size());
        assertTrue(candidateRows.stream().allMatch(row -> ((Number) row.getAs("label_rank")).intValue() < 0));
        assertTrue(candidateRows.stream().allMatch(row -> row.<Number>getAs("label_rank").intValue()
                == row.<Number>getAs("dissimilarity_rank").intValue()));
    }

    @Test
    void reportsMissingReferenceBeforeTryingToCalculateCandidateScores() {
        SparkCanvasTable references = geometryTable("empty_reference_raw", "empty_reference", List.of(
                stringColumn("id"), doubleColumn("a"), stringColumn("wkt")), List.of());
        SparkCanvasTable candidates = geometryTable("candidate_for_empty_reference_raw",
                "candidate_for_empty_reference", List.of(
                        stringColumn("id"), doubleColumn("a"), stringColumn("wkt")),
                List.of(RowFactory.create("c1", 1d, "POINT (1 1)")));
        SpatialSimilarLocationsConfiguration base = configuration(
                SpatialSimilarLocationsMatchMethod.ATTRIBUTE_VALUES,
                SpatialSimilarLocationsResultMode.MOST_SIMILAR,
                List.of(new SpatialSimilarLocationsAnalysisField("a", "a")), List.of(), null, null);
        SpatialSimilarLocationsConfiguration configuration = new SpatialSimilarLocationsConfiguration(
                "empty_reference", "id", "shape", null,
                "candidate_for_empty_reference", "id", "shape", null,
                base.analysisFields(), base.appendFields(), base.matchMethod(), base.resultMode(),
                base.numberOfResults(), base.outputTableName(), base.outputGeometryColumnName(),
                base.locationTypeColumnName(), base.similarityRankColumnName(), base.dissimilarityRankColumnName(),
                base.similarityIndexColumnName(), base.cosineIndexColumnName(), base.labelRankColumnName(),
                base.referenceIdOutputColumnName(), base.searchIdOutputColumnName());
        RecordingIssueSink issues = new RecordingIssueSink();
        CanvasNodeOperationResult result = operator(configuration,
                Map.of("empty_reference", references, "candidate_for_empty_reference", candidates), issues);

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> result.propagatedTables().get("similar_locations").dataset().collectAsList());
        assertTrue(messageChain(failure).contains("SPATIAL_SIMILAR_LOCATIONS_REFERENCE_REQUIRED"),
                () -> messageChain(failure));
    }

    @Test
    void previewHasCompleteReferenceAndCandidateLineageWithoutSubmittingSparkJobs() {
        SparkCanvasTable references = markInput(geometryTable(
                "lineage_references_raw", "references",
                List.of(stringColumn("id"), stringColumn("status"),
                        doubleColumn("a"), doubleColumn("b"), stringColumn("wkt")),
                List.of()), "references");
        SparkCanvasTable candidates = markInput(geometryTable(
                "lineage_candidates_raw", "candidates",
                List.of(stringColumn("id"), stringColumn("status"), stringColumn("name"),
                        doubleColumn("a"), doubleColumn("b"), stringColumn("wkt")),
                List.of()), "candidates");
        SpatialSimilarLocationsConfiguration configuration = configuration(
                SpatialSimilarLocationsMatchMethod.ATTRIBUTE_VALUES,
                SpatialSimilarLocationsResultMode.BOTH,
                List.of(new SpatialSimilarLocationsAnalysisField("a", "analysis_a"),
                        new SpatialSimilarLocationsAnalysisField("b", "analysis_b")),
                List.of(new SpatialSimilarLocationsAppendField("name", "candidate_name")),
                activeFilter(), activeFilter());
        Map<String, SparkCanvasTable> inputs = new LinkedHashMap<>();
        inputs.put("references", references);
        inputs.put("candidates", candidates);

        String jobGroup = "similar-locations-preview-" + id();
        spark.sparkContext().setJobGroup(jobGroup, "similar locations preview", false);
        TaskLineageEvidence.Flow flow;
        try {
            RecordingIssueSink issues = new RecordingIssueSink();
            CanvasNodeOperationResult result = new SpatialSimilarLocationsNodeOperator().apply(
                    new SpatialSimilarLocationsNodeDefinition(
                            id(), "查找相似位置", layout(), configuration),
                    inputs, previewContext(issues));
            assertFalse(issues.hasErrors(), issues::toString);
            flow = analyzeLineage(result.propagatedTables().get("similar_locations"));
            assertEquals(0, spark.sparkContext().statusTracker()
                    .getJobIdsForGroup(jobGroup).length);
        } finally {
            spark.sparkContext().clearJobGroup();
        }

        assertEquals(TaskLineageEvidence.Coverage.FIELD_COMPLETE, flow.coverage(),
                () -> flow.warnings().toString());
        assertTrue(flow.fields().stream().noneMatch(field ->
                        field.outputEffect()
                                == TaskLineageEvidence.OutputEffect.WRITTEN_UNKNOWN_SOURCE),
                () -> flow.fields().toString());
        assertEdge(flow, "geometry", "references", "shape");
        assertEdge(flow, "geometry", "candidates", "shape");
        assertEdge(flow, "reference_id", "references", "id");
        assertEdge(flow, "search_id", "candidates", "id");
        assertEdge(flow, "analysis_a", "references", "a");
        assertEdge(flow, "analysis_a", "candidates", "a");
        assertEdge(flow, "candidate_name", "candidates", "name");
        for (String output : List.of("location_type", "similarity_rank", "dissimilarity_rank",
                "simindex", "cosimindex", "label_rank")) {
            assertEdge(flow, output, "references", "a");
            assertEdge(flow, output, "candidates", "a");
            assertEdge(flow, output, "references", "status");
            assertEdge(flow, output, "candidates", "status");
        }
    }

    @Test
    void ranksTwentyThousandCandidatesAndReturnsTenThousandMostSimilar() {
        Dataset<Row> referenceData = spark.range(1).select(
                functions.concat(functions.lit("R"), functions.col("id")).alias("id"),
                functions.lit(0d).alias("a"),
                st_constructors.ST_Point(functions.lit(0d), functions.lit(0d)).alias("shape"));
        Dataset<Row> candidateData = spark.range(20_000).select(
                functions.concat(functions.lit("C"), functions.col("id")).alias("id"),
                functions.col("id").cast("double").alias("a"),
                st_constructors.ST_Point(functions.col("id").cast("double"), functions.lit(0d))
                        .alias("shape"));
        List<CanvasColumnSchema> columns = List.of(
                stringColumn("id"), doubleColumn("a"), geometryColumn("shape"));
        SparkCanvasTable references = new SparkCanvasTable(
                new CanvasTableSchema("references", null, columns,
                        CanvasDatasetKind.BOUNDED, null, null), referenceData);
        SparkCanvasTable candidates = new SparkCanvasTable(
                new CanvasTableSchema("candidates", null, columns,
                        CanvasDatasetKind.BOUNDED, null, null), candidateData);
        SpatialSimilarLocationsConfiguration base = configuration(
                SpatialSimilarLocationsMatchMethod.ATTRIBUTE_VALUES,
                SpatialSimilarLocationsResultMode.MOST_SIMILAR,
                List.of(new SpatialSimilarLocationsAnalysisField("a", "a")),
                List.of(), null, null);
        SpatialSimilarLocationsConfiguration configuration = new SpatialSimilarLocationsConfiguration(
                base.referenceTableName(), base.referenceIdColumnName(), base.referenceGeometryColumnName(), null,
                base.candidateTableName(), base.candidateIdColumnName(), base.candidateGeometryColumnName(), null,
                base.analysisFields(), base.appendFields(), base.matchMethod(), base.resultMode(), 10_000,
                base.outputTableName(), base.outputGeometryColumnName(), base.locationTypeColumnName(),
                base.similarityRankColumnName(), base.dissimilarityRankColumnName(),
                base.similarityIndexColumnName(), base.cosineIndexColumnName(), base.labelRankColumnName(),
                base.referenceIdOutputColumnName(), base.searchIdOutputColumnName());
        RecordingIssueSink issues = new RecordingIssueSink();
        Map<String, SparkCanvasTable> inputs = new LinkedHashMap<>();
        inputs.put("references", references);
        inputs.put("candidates", candidates);

        Dataset<Row> output = operator(configuration, inputs, issues)
                .propagatedTables().get("similar_locations").dataset();
        assertFalse(issues.hasErrors(), issues::toString);
        Row summary = output.agg(
                functions.count(functions.lit(1)).alias("row_count"),
                functions.sum(functions.when(output.col("location_type").equalTo("CANDIDATE"), 1L)
                        .otherwise(0L)).alias("candidate_count"),
                functions.max(output.col("similarity_rank")).alias("max_rank"))
                .first();
        assertEquals(10_001L, ((Number) summary.getAs("row_count")).longValue());
        assertEquals(10_000L, ((Number) summary.getAs("candidate_count")).longValue());
        assertEquals(10_000, ((Number) summary.getAs("max_rank")).intValue());
    }

    private CanvasNodeOperationResult operator(
            SpatialSimilarLocationsConfiguration configuration,
            Map<String, SparkCanvasTable> inputs,
            RecordingIssueSink issues
    ) {
        return new SpatialSimilarLocationsNodeOperator().apply(
                new SpatialSimilarLocationsNodeDefinition(id(), "查找相似位置", layout(), configuration),
                inputs, runtimeContext(issues));
    }

    private SpatialSimilarLocationsConfiguration configuration(
            SpatialSimilarLocationsMatchMethod method,
            SpatialSimilarLocationsResultMode resultMode,
            List<SpatialSimilarLocationsAnalysisField> analyses,
            List<SpatialSimilarLocationsAppendField> appends,
            CanvasFilterCondition referenceFilter,
            CanvasFilterCondition candidateFilter
    ) {
        return new SpatialSimilarLocationsConfiguration(
                "references", "id", "shape", referenceFilter,
                "candidates", "id", "shape", candidateFilter,
                analyses, appends, method, resultMode, 1, "similar_locations", "geometry",
                "location_type", "similarity_rank", "dissimilarity_rank", "simindex",
                "cosimindex", "label_rank", "reference_id", "search_id");
    }

    private CanvasFieldPredicate activeFilter() {
        return new CanvasFieldPredicate("status", FilterOperator.EQUALS,
                List.of(new CanvasLiteral(PlatformDataType.STRING, "active")));
    }

    private SparkCanvasTable geometryTable(
            String rawName,
            String outputName,
            List<CanvasColumnSchema> columns,
            List<Row> rows
    ) {
        SparkCanvasTable raw = table(rawName, columns, rows);
        RecordingIssueSink issues = new RecordingIssueSink();
        CanvasNodeOperationResult result = new GeometryConstructNodeOperator().apply(
                new GeometryConstructNodeDefinition(id(), "构造", layout(),
                        new GeometryConstructConfiguration(rawName, outputName, "shape",
                                new GeometryConstructSource.Wkt("wkt"),
                                new GeometryTypeDefinition(GeometryKind.POINT,
                                        new CrsReference("EPSG", 3857), CoordinateDimension.XY))),
                Map.of(rawName, raw), previewContext(issues));
        assertFalse(issues.hasErrors(), issues::toString);
        return result.propagatedTables().get(outputName);
    }

    private CanvasNodeOperationContext previewContext(RecordingIssueSink issues) {
        return new CanvasNodeOperationContext(spark,
                MetadataIndex.create(new MetadataSnapshot(List.of(), List.of())),
                issues, new SchemaOnlyCanvasNodeDataAccess(spark));
    }

    private CanvasNodeOperationContext runtimeContext(RecordingIssueSink issues) {
        return new CanvasNodeOperationContext(spark,
                MetadataIndex.create(new MetadataSnapshot(List.of(), List.of())),
                issues, new SchemaOnlyCanvasNodeDataAccess(spark), CanvasExecutionMode.BATCH,
                CanvasRuntimeValues.execution(UUID.randomUUID(), Instant.now()));
    }

    private SparkCanvasTable table(String name, List<CanvasColumnSchema> columns, List<Row> rows) {
        CanvasTableSchema schema = new CanvasTableSchema(name, null, columns,
                CanvasDatasetKind.BOUNDED, null, null);
        return new SparkCanvasTable(schema, spark.createDataFrame(rows, SparkTypeMapper.toStructType(columns)));
    }

    private static CanvasColumnSchema stringColumn(String name) {
        return column(name, PlatformDataType.STRING);
    }

    private static CanvasColumnSchema doubleColumn(String name) {
        return column(name, PlatformDataType.DOUBLE);
    }

    private static CanvasColumnSchema geometryColumn(String name) {
        return new CanvasColumnSchema(name, PlatformDataType.GEOMETRY, null, null, null, false,
                null, false, false, null,
                new GeometryTypeDefinition(GeometryKind.POINT,
                        new CrsReference("EPSG", 3857), CoordinateDimension.XY));
    }

    private SparkCanvasTable markInput(SparkCanvasTable table, String key) {
        var asset = new TaskLineageEvidence.Asset(
                key, TaskLineageEvidence.AssetRole.INPUT, TaskLineageEvidence.AssetKind.JDBC_TABLE,
                null, null, null, null, UUID.randomUUID(), null, null, key, null, null, key);
        var fields = new LinkedHashMap<String, CatalystLineageMetadata.InputField>();
        for (CanvasColumnSchema column : table.schema().columns()) {
            fields.put(column.name(), new CatalystLineageMetadata.InputField(
                    key + ":" + column.name(), null));
        }
        return new SparkCanvasTable(table.schema(), CatalystLineageMetadata.markInput(
                table.dataset(), key + "-node", asset, fields));
    }

    private TaskLineageEvidence.Flow analyzeLineage(SparkCanvasTable table) {
        var outputAsset = new TaskLineageEvidence.Asset(
                "output", TaskLineageEvidence.AssetRole.OUTPUT, TaskLineageEvidence.AssetKind.JDBC_TABLE,
                null, TaskLineageEvidence.WriteMode.APPEND, null, null, UUID.randomUUID(),
                null, null, "similar_locations", null, null, "similar_locations");
        var candidate = new CatalystLineageOutputCandidate(
                "flow", "output-node", "JDBC_OUTPUT", "write", table.dataset(), outputAsset,
                table.schema().columns().stream().map(column ->
                        new CatalystLineageOutputCandidate.TargetField(
                                "out:" + column.name(), null, column.name(), column.name(),
                                TaskLineageEvidence.OutputEffect.WRITTEN_UNKNOWN_SOURCE)).toList());
        return new CatalystLineageAnalyzer().analyze(List.of(candidate)).flows().getFirst();
    }

    private static void assertEdge(
            TaskLineageEvidence.Flow flow,
            String target,
            String sourceTable,
            String sourceColumn
    ) {
        assertTrue(flow.fieldEdges().stream().anyMatch(edge ->
                        edge.target().localFieldKey().equals("out:" + target)
                                && edge.source().localFieldKey().equals(
                                sourceTable + ":" + sourceColumn)),
                () -> sourceTable + "." + sourceColumn + " -> " + target
                        + " missing in " + flow.fieldEdges());
    }

    private static CanvasColumnSchema column(String name, PlatformDataType type) {
        return new CanvasColumnSchema(name, type, null, null, null, true,
                null, false, false, null, null);
    }

    private static CanvasNodeLayout layout() {
        return new CanvasNodeLayout(0d, 0d, 392d, 244d);
    }

    private static String id() {
        return UUID.randomUUID().toString();
    }

    private static String messageChain(Throwable failure) {
        StringBuilder result = new StringBuilder();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            result.append(current.getMessage()).append('\n');
        }
        return result.toString();
    }

    private static final class RecordingIssueSink implements CanvasNodeIssueSink {
        private final List<String> codes = new ArrayList<>();
        private boolean errors;

        @Override
        public void error(String code, String message, String path) {
            codes.add(code + "@" + path);
            errors = true;
        }

        @Override
        public void warning(String code, String message, String path) {
            codes.add(code + "@" + path);
        }

        @Override
        public boolean hasErrors() {
            return errors;
        }

        @Override
        public String toString() {
            return codes.toString();
        }
    }
}
