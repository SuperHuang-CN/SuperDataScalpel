package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.JoinCondition;
import cn.superhuang.data.scalpel.contract.task.JoinOperator;
import cn.superhuang.data.scalpel.contract.task.JoinOutputColumn;
import cn.superhuang.data.scalpel.contract.task.JoinOutputColumnSource;
import cn.superhuang.data.scalpel.contract.task.JoinType;
import cn.superhuang.data.scalpel.contract.task.MetadataSnapshot;
import cn.superhuang.data.scalpel.contract.task.SpatialDurationUnit;
import cn.superhuang.data.scalpel.contract.task.SpatialJoinCondition;
import cn.superhuang.data.scalpel.contract.task.SpatialJoinConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialJoinNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialJoinOperation;
import cn.superhuang.data.scalpel.contract.task.SpatialJoinTemporalCondition;
import cn.superhuang.data.scalpel.contract.task.SpatialJoinTemporalRelationship;
import cn.superhuang.data.scalpel.contract.task.SpatialPredicate;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.datascalpel.taskengine.spark.SedonaSparkSupport;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SpatialJoinTemporalSupportSparkTest {
    private static final CanvasNodeLayout LAYOUT = new CanvasNodeLayout(0d, 0d, 368d, 224d);
    private SparkSession spark;

    @BeforeAll
    void startSpark() {
        spark = SedonaSparkSupport.initialize(SedonaSparkSupport.builder()
                .master("local[1]")
                .appName("spatial-join-temporal-support-test")
                .config("spark.ui.enabled", "false")
                .config("spark.driver.host", "127.0.0.1")
                .config("spark.driver.bindAddress", "127.0.0.1")
                .config("spark.sql.caseSensitive", "true")
                .getOrCreate());
    }

    @AfterAll
    void stopSpark() {
        if (spark != null) spark.stop();
    }

    @Test
    void evaluatesAllArcGisTemporalRelationshipsWithLeftAsTarget() {
        List<RelationshipExample> examples = List.of(
                example(SpatialJoinTemporalRelationship.EQUALS, 2, 5, 2, 5, null),
                example(SpatialJoinTemporalRelationship.INTERSECTS, 2, 5, 5, 8, null),
                example(SpatialJoinTemporalRelationship.DURING, 3, 4, 2, 5,
                        SpatialJoinTemporalRelationship.CONTAINS),
                example(SpatialJoinTemporalRelationship.CONTAINS, 2, 5, 3, 4,
                        SpatialJoinTemporalRelationship.DURING),
                example(SpatialJoinTemporalRelationship.FINISHES, 3, 5, 2, 5,
                        SpatialJoinTemporalRelationship.FINISHED_BY),
                example(SpatialJoinTemporalRelationship.FINISHED_BY, 2, 5, 3, 5,
                        SpatialJoinTemporalRelationship.FINISHES),
                example(SpatialJoinTemporalRelationship.MEETS, 2, 5, 5, 8,
                        SpatialJoinTemporalRelationship.MET_BY),
                example(SpatialJoinTemporalRelationship.MET_BY, 5, 8, 2, 5,
                        SpatialJoinTemporalRelationship.MEETS),
                example(SpatialJoinTemporalRelationship.OVERLAPS, 2, 6, 5, 8,
                        SpatialJoinTemporalRelationship.OVERLAPPED_BY),
                example(SpatialJoinTemporalRelationship.OVERLAPPED_BY, 5, 8, 2, 6,
                        SpatialJoinTemporalRelationship.OVERLAPS),
                example(SpatialJoinTemporalRelationship.STARTS, 2, 4, 2, 5,
                        SpatialJoinTemporalRelationship.STARTED_BY),
                example(SpatialJoinTemporalRelationship.STARTED_BY, 2, 5, 2, 4,
                        SpatialJoinTemporalRelationship.STARTS),
                example(SpatialJoinTemporalRelationship.NEAR, 2, 5, 7, 8, null),
                example(SpatialJoinTemporalRelationship.NEAR_BEFORE, 2, 5, 7, 8,
                        SpatialJoinTemporalRelationship.NEAR_AFTER),
                example(SpatialJoinTemporalRelationship.NEAR_AFTER, 7, 8, 2, 5,
                        SpatialJoinTemporalRelationship.NEAR_BEFORE)
        );

        for (RelationshipExample example : examples) {
            assertEquals(1L, matchCount(
                    timestampInputs(example.leftStart(), example.leftEnd(),
                            example.rightStart(), example.rightEnd(), 7L, 7L),
                    condition(example.relationship(), 2L, SpatialDurationUnit.SECONDS),
                    JoinType.INNER,
                    List.of()), example.relationship().name());
            if (example.counterRelationship() != null) {
                assertEquals(0L, matchCount(
                        timestampInputs(example.leftStart(), example.leftEnd(),
                                example.rightStart(), example.rightEnd(), 7L, 7L),
                        condition(example.counterRelationship(), 2L, SpatialDurationUnit.SECONDS),
                        JoinType.INNER,
                        List.of()), "direction of " + example.relationship());
            }
        }
    }

    @Test
    void supportsInstantAndDateFieldsAndUsesInclusiveNearBoundary() {
        Map<String, SparkCanvasTable> instant = timestampInputs(3, null, 2, 4, 7L, 7L);
        assertEquals(1L, matchCount(
                instant,
                new SpatialJoinTemporalCondition(
                        SpatialJoinTemporalRelationship.DURING,
                        "start_time", null, "start_time", "end_time", null, null),
                JoinType.INNER,
                List.of()));

        Map<String, SparkCanvasTable> dates = dateInputs("2024-01-02", "2024-01-02");
        assertEquals(1L, matchCount(
                dates,
                new SpatialJoinTemporalCondition(
                        SpatialJoinTemporalRelationship.EQUALS,
                        "start_time", null, "start_time", null, null, null),
                JoinType.INNER,
                List.of()));

        assertEquals(1L, matchCount(
                timestampNtzInputs(timestamp(2), timestamp(5), timestamp(3), timestamp(4)),
                condition(SpatialJoinTemporalRelationship.CONTAINS, null, null),
                JoinType.INNER,
                List.of()));

        assertEquals(1L, matchCount(
                timestampInputs("00:00:00.000000", "00:00:01.000000",
                        "00:00:02.500000", "00:00:03.000000", 7L, 7L),
                condition(SpatialJoinTemporalRelationship.NEAR_BEFORE,
                        1500L, SpatialDurationUnit.MILLISECONDS),
                JoinType.INNER,
                List.of()));
        assertEquals(0L, matchCount(
                timestampInputs("00:00:00.000000", "00:00:01.000000",
                        "00:00:02.500001", "00:00:03.000000", 7L, 7L),
                condition(SpatialJoinTemporalRelationship.NEAR_BEFORE,
                        1500L, SpatialDurationUnit.MILLISECONDS),
                JoinType.INNER,
                List.of()));
    }

    @Test
    void convertsAllFixedNearUnits() {
        List<NearUnitExample> examples = List.of(
                new NearUnitExample(1500L, SpatialDurationUnit.MILLISECONDS,
                        "2024-01-01 00:00:02.500000"),
                new NearUnitExample(2L, SpatialDurationUnit.SECONDS,
                        "2024-01-01 00:00:03.000000"),
                new NearUnitExample(2L, SpatialDurationUnit.MINUTES,
                        "2024-01-01 00:02:01.000000"),
                new NearUnitExample(2L, SpatialDurationUnit.HOURS,
                        "2024-01-01 02:00:01.000000"),
                new NearUnitExample(2L, SpatialDurationUnit.DAYS,
                        "2024-01-03 00:00:01.000000"),
                new NearUnitExample(2L, SpatialDurationUnit.WEEKS,
                        "2024-01-15 00:00:01.000000")
        );
        for (NearUnitExample example : examples) {
            assertEquals(1L, matchCount(
                    timestampInputs(
                            "2024-01-01 00:00:00.000000",
                            "2024-01-01 00:00:01.000000",
                            example.rightStart(),
                            example.rightStart(),
                            7L,
                            7L),
                    condition(SpatialJoinTemporalRelationship.NEAR_BEFORE,
                            example.distance(), example.unit()),
                    JoinType.INNER,
                    List.of()), example.unit().name());
        }
    }

    @Test
    void nullInvalidIntervalsAndNonMatchingRowsStayUnmatchedInLeftJoin() {
        Dataset<Row> leftDataset = spark.sql("""
                SELECT 1L AS left_id, 7L AS tenant_id,
                       ST_GeomFromWKT('POINT (0 0)') AS shape,
                       CAST(NULL AS TIMESTAMP) AS start_time,
                       TIMESTAMP '2024-01-01 00:00:05' AS end_time
                UNION ALL
                SELECT 2L, 7L, ST_GeomFromWKT('POINT (0 0)'),
                       TIMESTAMP '2024-01-01 00:00:08',
                       TIMESTAMP '2024-01-01 00:00:05'
                UNION ALL
                SELECT 3L, 7L, ST_GeomFromWKT('POINT (0 0)'),
                       TIMESTAMP '2024-01-01 00:00:20',
                       TIMESTAMP '2024-01-01 00:00:21'
                """);
        Dataset<Row> rightDataset = spark.sql("""
                SELECT 10L AS right_id, 7L AS tenant_id,
                       ST_GeomFromWKT('POINT (0 0)') AS shape,
                       TIMESTAMP '2024-01-01 00:00:02' AS start_time,
                       TIMESTAMP '2024-01-01 00:00:05' AS end_time
                """);
        Map<String, SparkCanvasTable> inputs = Map.of(
                "targets", new SparkCanvasTable(timestampSchema("targets", "left_id"), leftDataset),
                "joins", new SparkCanvasTable(timestampSchema("joins", "right_id"), rightDataset));

        List<Row> rows = rows(inputs,
                condition(SpatialJoinTemporalRelationship.INTERSECTS,
                        null, null), JoinType.LEFT, List.of());

        assertEquals(3, rows.size());
        assertTrue(rows.stream().allMatch(row -> row.isNullAt(row.fieldIndex("right_id"))));
    }

    @Test
    void combinesTemporalSpatialAndAttributeConditionsWithAnd() {
        Map<String, SparkCanvasTable> inputs = timestampInputs(2, 5, 2, 5, 7L, 9L);

        assertEquals(0L, matchCount(
                inputs,
                condition(SpatialJoinTemporalRelationship.EQUALS, null, null),
                JoinType.INNER,
                List.of(new JoinCondition("tenant_id", JoinOperator.EQUALS, "tenant_id"))));
    }

    @Test
    void validatesTemporalColumnsTypesThresholdAndOverflowBeforePlanning() {
        List<ValidationExample> examples = List.of(
                new ValidationExample(
                        new SpatialJoinTemporalCondition(
                                SpatialJoinTemporalRelationship.INTERSECTS,
                                "missing", null, "start_time", null, null, null),
                        timestampInputs(2, 5, 2, 5, 7L, 7L),
                        "COLUMN_NOT_FOUND"),
                new ValidationExample(
                        new SpatialJoinTemporalCondition(
                                SpatialJoinTemporalRelationship.INTERSECTS,
                                "start_time", null, "start_time", null, null, null),
                        mixedTypeInputs(),
                        "SPATIAL_JOIN_TEMPORAL_TYPE_MISMATCH"),
                new ValidationExample(
                        condition(SpatialJoinTemporalRelationship.NEAR, 0L,
                                SpatialDurationUnit.SECONDS),
                        timestampInputs(2, 5, 2, 5, 7L, 7L),
                        "INVALID_SPATIAL_JOIN_TEMPORAL_NEAR_DISTANCE"),
                new ValidationExample(
                        condition(SpatialJoinTemporalRelationship.NEAR, Long.MAX_VALUE,
                                SpatialDurationUnit.WEEKS),
                        timestampInputs(2, 5, 2, 5, 7L, 7L),
                        "SPATIAL_JOIN_TEMPORAL_NEAR_DISTANCE_OVERFLOW")
        );

        for (ValidationExample example : examples) {
            RecordingIssueSink issues = new RecordingIssueSink();
            new SpatialJoinNodeOperator().apply(
                    definition(example.condition(), JoinType.INNER, List.of()),
                    example.inputs(),
                    context(issues));
            assertTrue(issues.codes.contains(example.expectedCode()),
                    () -> example.expectedCode() + ": " + issues.codes);
        }
    }

    private long matchCount(
            Map<String, SparkCanvasTable> inputs,
            SpatialJoinTemporalCondition condition,
            JoinType joinType,
            List<JoinCondition> attributeConditions
    ) {
        return rows(inputs, condition, joinType, attributeConditions).size();
    }

    private List<Row> rows(
            Map<String, SparkCanvasTable> inputs,
            SpatialJoinTemporalCondition condition,
            JoinType joinType,
            List<JoinCondition> attributeConditions
    ) {
        RecordingIssueSink issues = new RecordingIssueSink();
        CanvasNodeOperationResult result = new SpatialJoinNodeOperator().apply(
                definition(condition, joinType, attributeConditions), inputs, context(issues));
        assertFalse(issues.hasErrors(), () -> issues.codes.toString());
        return result.propagatedTables().get("matched").dataset().collectAsList();
    }

    private SpatialJoinNodeDefinition definition(
            SpatialJoinTemporalCondition condition,
            JoinType joinType,
            List<JoinCondition> attributeConditions
    ) {
        return new SpatialJoinNodeDefinition(
                UUID.randomUUID().toString(),
                "时空连接",
                LAYOUT,
                new SpatialJoinConfiguration(
                        "targets",
                        "joins",
                        "matched",
                        joinType,
                        List.of(new SpatialJoinCondition(
                                "shape", SpatialPredicate.EQUALS, "shape")),
                        attributeConditions,
                        List.of(
                                new JoinOutputColumn(
                                        JoinOutputColumnSource.LEFT,
                                        "left_id", "left_id", true),
                                new JoinOutputColumn(
                                        JoinOutputColumnSource.RIGHT,
                                        "right_id", "right_id", true)),
                        SpatialJoinOperation.JOIN_ONE_TO_MANY,
                        null,
                        condition
                )
        );
    }

    private Map<String, SparkCanvasTable> timestampInputs(
            int leftStart,
            Integer leftEnd,
            int rightStart,
            Integer rightEnd,
            long leftTenant,
            long rightTenant
    ) {
        return timestampInputs(
                timestamp(leftStart), leftEnd == null ? null : timestamp(leftEnd),
                timestamp(rightStart), rightEnd == null ? null : timestamp(rightEnd),
                leftTenant, rightTenant);
    }

    private Map<String, SparkCanvasTable> timestampInputs(
            String leftStart,
            String leftEnd,
            String rightStart,
            String rightEnd,
            long leftTenant,
            long rightTenant
    ) {
        Dataset<Row> left = temporalDataset(
                "left_id", 1L, leftTenant, leftStart, leftEnd, "TIMESTAMP");
        Dataset<Row> right = temporalDataset(
                "right_id", 10L, rightTenant, rightStart, rightEnd, "TIMESTAMP");
        return Map.of(
                "targets", new SparkCanvasTable(timestampSchema("targets", "left_id"), left),
                "joins", new SparkCanvasTable(timestampSchema("joins", "right_id"), right));
    }

    private Map<String, SparkCanvasTable> dateInputs(String leftDate, String rightDate) {
        Dataset<Row> left = temporalDataset(
                "left_id", 1L, 7L, leftDate, null, "DATE");
        Dataset<Row> right = temporalDataset(
                "right_id", 10L, 7L, rightDate, null, "DATE");
        return Map.of(
                "targets", new SparkCanvasTable(dateSchema("targets", "left_id"), left),
                "joins", new SparkCanvasTable(dateSchema("joins", "right_id"), right));
    }

    private Map<String, SparkCanvasTable> timestampNtzInputs(
            String leftStart,
            String leftEnd,
            String rightStart,
            String rightEnd
    ) {
        Dataset<Row> left = temporalDataset(
                "left_id", 1L, 7L, leftStart, leftEnd, "TIMESTAMP_NTZ");
        Dataset<Row> right = temporalDataset(
                "right_id", 10L, 7L, rightStart, rightEnd, "TIMESTAMP_NTZ");
        return Map.of(
                "targets", new SparkCanvasTable(
                        temporalSchema("targets", "left_id", PlatformDataType.TIMESTAMP_NTZ),
                        left),
                "joins", new SparkCanvasTable(
                        temporalSchema("joins", "right_id", PlatformDataType.TIMESTAMP_NTZ),
                        right));
    }

    private Map<String, SparkCanvasTable> mixedTypeInputs() {
        Dataset<Row> left = temporalDataset(
                "left_id", 1L, 7L, timestamp(2), null, "TIMESTAMP");
        Dataset<Row> right = temporalDataset(
                "right_id", 10L, 7L, "2024-01-01", null, "DATE");
        return Map.of(
                "targets", new SparkCanvasTable(timestampSchema("targets", "left_id"), left),
                "joins", new SparkCanvasTable(dateSchema("joins", "right_id"), right));
    }

    private Dataset<Row> temporalDataset(
            String idColumn,
            long id,
            long tenantId,
            String start,
            String end,
            String type
    ) {
        String startSql = temporalLiteral(start, type);
        String endSql = end == null ? startSql : temporalLiteral(end, type);
        return spark.sql("SELECT " + id + "L AS " + idColumn
                + ", " + tenantId + "L AS tenant_id"
                + ", ST_GeomFromWKT('POINT (0 0)') AS shape"
                + ", " + startSql + " AS start_time"
                + ", " + endSql + " AS end_time");
    }

    private static String temporalLiteral(String value, String type) {
        String normalized = value.contains(" ") || type.equals("DATE")
                ? value : "2024-01-01 " + value;
        return type + " '" + normalized + "'";
    }

    private static String timestamp(int seconds) {
        return "2024-01-01 00:00:" + String.format("%02d", seconds) + ".000000";
    }

    private static CanvasTableSchema timestampSchema(String name, String idColumn) {
        return temporalSchema(name, idColumn, PlatformDataType.TIMESTAMP);
    }

    private static CanvasTableSchema dateSchema(String name, String idColumn) {
        return temporalSchema(name, idColumn, PlatformDataType.DATE);
    }

    private static CanvasTableSchema temporalSchema(
            String name,
            String idColumn,
            PlatformDataType temporalType
    ) {
        return new CanvasTableSchema(name, null, List.of(
                column(idColumn, PlatformDataType.LONG, false),
                column("tenant_id", PlatformDataType.LONG, false),
                geometry("shape"),
                column("start_time", temporalType, true),
                column("end_time", temporalType, true)
        ));
    }

    private static CanvasColumnSchema column(
            String name,
            PlatformDataType type,
            boolean nullable
    ) {
        return new CanvasColumnSchema(
                name, type, null, null, null, nullable, null, false, false, null);
    }

    private static CanvasColumnSchema geometry(String name) {
        return new CanvasColumnSchema(
                name,
                PlatformDataType.GEOMETRY,
                null,
                null,
                null,
                false,
                null,
                false,
                false,
                null,
                new GeometryTypeDefinition(
                        GeometryKind.POINT,
                        CrsReference.epsg(4326),
                        CoordinateDimension.XY)
        );
    }

    private CanvasNodeOperationContext context(RecordingIssueSink issues) {
        return new CanvasNodeOperationContext(
                spark,
                MetadataIndex.create(new MetadataSnapshot(List.of(), List.of())),
                issues,
                new SchemaOnlyCanvasNodeDataAccess(spark));
    }

    private static SpatialJoinTemporalCondition condition(
            SpatialJoinTemporalRelationship relationship,
            Long distance,
            SpatialDurationUnit unit
    ) {
        return new SpatialJoinTemporalCondition(
                relationship,
                "start_time",
                "end_time",
                "start_time",
                "end_time",
                distance,
                unit);
    }

    private static RelationshipExample example(
            SpatialJoinTemporalRelationship relationship,
            int leftStart,
            int leftEnd,
            int rightStart,
            int rightEnd,
            SpatialJoinTemporalRelationship counterRelationship
    ) {
        return new RelationshipExample(
                relationship, leftStart, leftEnd, rightStart, rightEnd, counterRelationship);
    }

    private record RelationshipExample(
            SpatialJoinTemporalRelationship relationship,
            int leftStart,
            int leftEnd,
            int rightStart,
            int rightEnd,
            SpatialJoinTemporalRelationship counterRelationship
    ) {
    }

    private record NearUnitExample(
            long distance,
            SpatialDurationUnit unit,
            String rightStart
    ) {
    }

    private record ValidationExample(
            SpatialJoinTemporalCondition condition,
            Map<String, SparkCanvasTable> inputs,
            String expectedCode
    ) {
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
