package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasLiteral;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.DropNullRowsRule;
import cn.superhuang.data.scalpel.contract.task.FillNullLiteralRule;
import cn.superhuang.data.scalpel.contract.task.MetadataSnapshot;
import cn.superhuang.data.scalpel.contract.task.NullHandlingConfiguration;
import cn.superhuang.data.scalpel.contract.task.NullHandlingNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.NullMatchMode;
import cn.superhuang.data.scalpel.contract.task.NullOrdering;
import cn.superhuang.data.scalpel.contract.task.RowsFrameBoundary;
import cn.superhuang.data.scalpel.contract.task.RowsWindowFrame;
import cn.superhuang.data.scalpel.contract.task.SortDirection;
import cn.superhuang.data.scalpel.contract.task.SortField;
import cn.superhuang.data.scalpel.contract.task.TopNConfiguration;
import cn.superhuang.data.scalpel.contract.task.TopNNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.TopNTieStrategy;
import cn.superhuang.data.scalpel.contract.task.ValueMappingConfiguration;
import cn.superhuang.data.scalpel.contract.task.ValueMappingEntry;
import cn.superhuang.data.scalpel.contract.task.ValueMappingNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ValueMappingRule;
import cn.superhuang.data.scalpel.contract.task.ValueMappingUnmatchedStrategy;
import cn.superhuang.data.scalpel.contract.task.WindowConfiguration;
import cn.superhuang.data.scalpel.contract.task.WindowFrameType;
import cn.superhuang.data.scalpel.contract.task.WindowFunctionItem;
import cn.superhuang.data.scalpel.contract.task.WindowNodeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

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
class AdvancedProcessorNodeOperatorSparkTest {
    private static final CanvasNodeLayout LAYOUT =
            new CanvasNodeLayout(0d, 0d, 240d, 120d);

    private SparkSession spark;

    @BeforeAll
    void startSpark() {
        spark = SparkSession.builder()
                .master("local[1]")
                .appName("canvas-advanced-processor-operator-test")
                .config("spark.ui.enabled", "false")
                .config("spark.driver.host", "127.0.0.1")
                .config("spark.driver.bindAddress", "127.0.0.1")
                .config("spark.sql.caseSensitive", "true")
                .config("spark.sql.ansi.enabled", "true")
                .config("spark.sql.session.timeZone", "UTC")
                .getOrCreate();
    }

    @AfterAll
    void stopSpark() {
        if (spark != null) {
            spark.stop();
        }
    }

    @Test
    void handlesAnyAllFillAndNaNWithoutTreatingNaNAsSqlNull() {
        SparkCanvasTable source = mappingSource();

        CanvasNodeOperationResult anyResult = applyNullHandling(
                source,
                "any_cleaned",
                List.of(new DropNullRowsRule(List.of("score"), NullMatchMode.ANY_NULL))
        );
        List<Long> anyIds = ids(anyResult, "any_cleaned");
        assertEquals(List.of(1L, 3L, 4L, 5L, 7L), anyIds);
        assertTrue(Double.isNaN(
                row(anyResult, "any_cleaned", 3L).getAs("score")
        ));

        CanvasNodeOperationResult allResult = applyNullHandling(
                source,
                "all_cleaned",
                List.of(new DropNullRowsRule(
                        List.of("score", "status"),
                        NullMatchMode.ALL_NULL
                ))
        );
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 7L), ids(allResult, "all_cleaned"));

        CanvasNodeOperationResult filledResult = applyNullHandling(
                source,
                "filled",
                List.of(new FillNullLiteralRule(
                        "score",
                        literal(PlatformDataType.DOUBLE, "0")
                ))
        );
        assertEquals(0d, row(filledResult, "filled", 2L).getAs("score"));
        assertEquals(0d, row(filledResult, "filled", 6L).getAs("score"));
        assertTrue(Double.isNaN(
                row(filledResult, "filled", 3L).getAs("score")
        ));
        assertFalse(column(filledResult, "filled", "score").nullable());
        assertEquals(CanvasDatasetKind.BOUNDED,
                filledResult.propagatedTables().get("filled").schema().datasetKind());
    }

    @Test
    void mapsValuesWithNullLiteralAndUnmatchedStrategiesAndFailsSafely() {
        SparkCanvasTable source = mappingSource();
        ValueMappingRule rule = new ValueMappingRule(
                "status",
                List.of(
                        new ValueMappingEntry(
                                literal(PlatformDataType.STRING, "P"),
                                literal(PlatformDataType.STRING, "PAID")
                        ),
                        new ValueMappingEntry(
                                literal(PlatformDataType.STRING, "X"),
                                null
                        )
                ),
                ValueMappingUnmatchedStrategy.SET_LITERAL,
                literal(PlatformDataType.STRING, "UNKNOWN")
        );

        CanvasNodeOperationResult mapped = applyValueMapping(
                source,
                "mapped",
                List.of(rule),
                new RecordingIssueSink()
        );
        Map<Long, String> statuses = new LinkedHashMap<>();
        mapped.propagatedTables().get("mapped").dataset()
                .orderBy("id")
                .collectAsList()
                .forEach(item -> statuses.put(item.getAs("id"), item.getAs("status")));
        assertEquals("PAID", statuses.get(1L));
        assertEquals("PAID", statuses.get(3L));
        assertEquals("UNKNOWN", statuses.get(7L));
        assertTrue(statuses.containsKey(2L));
        assertEquals(null, statuses.get(2L));
        assertEquals(null, statuses.get(4L));
        assertEquals(null, statuses.get(5L));
        assertEquals(null, statuses.get(6L));

        ValueMappingRule strictRule = new ValueMappingRule(
                "status",
                List.of(new ValueMappingEntry(
                        literal(PlatformDataType.STRING, "P"),
                        literal(PlatformDataType.STRING, "PAID")
                )),
                ValueMappingUnmatchedStrategy.ERROR,
                null
        );
        CanvasNodeOperationResult strict = applyValueMapping(
                source,
                "strict_mapped",
                List.of(strictRule),
                new RecordingIssueSink()
        );
        RuntimeException failure = assertThrows(
                RuntimeException.class,
                () -> strict.propagatedTables().get("strict_mapped").dataset().collectAsList()
        );
        assertTrue(messageChain(failure).contains("VALUE_MAPPING_UNMATCHED_VALUE column=status"));
        assertFalse(messageChain(failure).contains("unmatched=X"));
        assertFalse(messageChain(failure).contains("unmatched=Y"));
    }

    @Test
    void rejectsSemanticallyDuplicateValueMappingSourcesBeforeExecution() {
        SparkCanvasTable source = decimalSource();
        RecordingIssueSink issues = new RecordingIssueSink();

        CanvasNodeOperationResult result = applyValueMapping(
                source,
                "mapped_decimal",
                List.of(new ValueMappingRule(
                        "amount",
                        List.of(
                                new ValueMappingEntry(
                                        literal(PlatformDataType.DECIMAL, "1.0"),
                                        literal(PlatformDataType.DECIMAL, "10")
                                ),
                                new ValueMappingEntry(
                                        literal(PlatformDataType.DECIMAL, "1.00"),
                                        literal(PlatformDataType.DECIMAL, "20")
                                )
                        ),
                        ValueMappingUnmatchedStrategy.KEEP,
                        null
                )),
                issues
        );

        assertTrue(issues.codes.contains("DUPLICATE_VALUE_MAPPING_SOURCE"));
        assertTrue(result.propagatedTables().isEmpty());
    }

    @Test
    void computesDeterministicWindowRankingLagAndRunningAggregate() {
        SparkCanvasTable source = rankingSource(false);
        RowsWindowFrame runningFrame = new RowsWindowFrame(
                WindowFrameType.ROWS,
                new RowsFrameBoundary.UnboundedPreceding(),
                new RowsFrameBoundary.CurrentRow()
        );
        WindowNodeDefinition node = new WindowNodeDefinition(
                UUID.randomUUID().toString(),
                "窗口计算",
                LAYOUT,
                new WindowConfiguration(
                        "scores",
                        "windowed_scores",
                        List.of("group_code"),
                        List.of(
                                sort("score", SortDirection.DESC),
                                sort("id", SortDirection.ASC)
                        ),
                        List.of(
                                new WindowFunctionItem.RowNumber("row_no"),
                                new WindowFunctionItem.Lag(
                                        "id",
                                        1,
                                        literal(PlatformDataType.LONG, "-1"),
                                        "previous_id"
                                ),
                                new WindowFunctionItem.Sum(
                                        "score",
                                        "running_score",
                                        runningFrame
                                )
                        )
                )
        );
        RecordingIssueSink issues = new RecordingIssueSink();

        CanvasNodeOperationResult result = new WindowNodeOperator().apply(
                node,
                Map.of("scores", source),
                context(issues)
        );

        assertFalse(issues.hasErrors(), () -> issues.codes.toString());
        List<Row> rows = result.propagatedTables().get("windowed_scores")
                .dataset()
                .orderBy("group_code", "id")
                .collectAsList();
        assertEquals(List.of(1, 2, 3, 1, 2),
                rows.stream().map(item -> item.<Integer>getAs("row_no")).toList());
        assertEquals(List.of(-1L, 1L, 2L, -1L, 4L),
                rows.stream().map(item -> item.<Long>getAs("previous_id")).toList());
        assertEquals(List.of(100d, 190d, 270d, 70d, 130d),
                rows.stream().map(item -> item.<Double>getAs("running_score")).toList());
        assertEquals(List.of(
                        "group_code", "id", "score",
                        "row_no", "previous_id", "running_score"
                ),
                result.propagatedTables().get("windowed_scores")
                        .schema().columns().stream().map(CanvasColumnSchema::name).toList());
    }

    @Test
    void appliesGlobalAndPartitionedTopNWithExactAndSqlRankTieSemantics() {
        SparkCanvasTable source = rankingSource(true);

        CanvasNodeOperationResult exactByGroup = applyTopN(
                source,
                "exact_by_group",
                List.of("group_code"),
                2,
                TopNTieStrategy.EXACT
        );
        Map<String, Long> exactCounts = exactByGroup.propagatedTables()
                .get("exact_by_group").dataset()
                .groupBy("group_code").count()
                .collectAsList().stream()
                .collect(java.util.stream.Collectors.toMap(
                        item -> item.getAs("group_code"),
                        item -> item.getAs("count")
                ));
        assertEquals(Map.of("A", 2L, "B", 2L), exactCounts);

        CanvasNodeOperationResult tiesByGroup = applyTopN(
                source,
                "ties_by_group",
                List.of("group_code"),
                2,
                TopNTieStrategy.WITH_TIES
        );
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L),
                ids(tiesByGroup, "ties_by_group"));

        CanvasNodeOperationResult globalExact = applyTopN(
                source,
                "global_exact",
                List.of(),
                2,
                TopNTieStrategy.EXACT
        );
        assertEquals(2L, globalExact.propagatedTables()
                .get("global_exact").dataset().count());
        assertEquals(List.of("group_code", "id", "score"),
                List.of(globalExact.propagatedTables()
                        .get("global_exact").dataset().columns()));
        assertEquals(source.schema().columns(),
                globalExact.propagatedTables().get("global_exact").schema().columns());
    }

    private CanvasNodeOperationResult applyNullHandling(
            SparkCanvasTable source,
            String outputTableName,
            List<cn.superhuang.data.scalpel.contract.task.NullHandlingRule> rules
    ) {
        RecordingIssueSink issues = new RecordingIssueSink();
        CanvasNodeOperationResult result = new NullHandlingNodeOperator().apply(
                new NullHandlingNodeDefinition(
                        UUID.randomUUID().toString(),
                        "空值处理",
                        LAYOUT,
                        new NullHandlingConfiguration("source", outputTableName, rules)
                ),
                Map.of("source", source),
                context(issues)
        );
        assertFalse(issues.hasErrors(), () -> issues.codes.toString());
        return result;
    }

    private CanvasNodeOperationResult applyValueMapping(
            SparkCanvasTable source,
            String outputTableName,
            List<ValueMappingRule> rules,
            RecordingIssueSink issues
    ) {
        return new ValueMappingNodeOperator().apply(
                new ValueMappingNodeDefinition(
                        UUID.randomUUID().toString(),
                        "值映射",
                        LAYOUT,
                        new ValueMappingConfiguration("source", outputTableName, rules)
                ),
                Map.of("source", source),
                context(issues)
        );
    }

    private CanvasNodeOperationResult applyTopN(
            SparkCanvasTable source,
            String outputTableName,
            List<String> partitions,
            int limit,
            TopNTieStrategy tieStrategy
    ) {
        RecordingIssueSink issues = new RecordingIssueSink();
        CanvasNodeOperationResult result = new TopNNodeOperator().apply(
                new TopNNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Top N",
                        LAYOUT,
                        new TopNConfiguration(
                                "scores",
                                outputTableName,
                                partitions,
                                List.of(sort("score", SortDirection.DESC)),
                                limit,
                                tieStrategy
                        )
                ),
                Map.of("scores", source),
                context(issues)
        );
        assertFalse(issues.hasErrors(), () -> issues.codes.toString());
        return result;
    }

    private CanvasNodeOperationContext context(RecordingIssueSink issues) {
        return new CanvasNodeOperationContext(
                spark,
                MetadataIndex.create(new MetadataSnapshot(List.of(), List.of())),
                issues,
                new SchemaOnlyCanvasNodeDataAccess(spark)
        );
    }

    private SparkCanvasTable mappingSource() {
        List<CanvasColumnSchema> columns = List.of(
                column("group_code", PlatformDataType.STRING, false),
                column("id", PlatformDataType.LONG, false),
                column("score", PlatformDataType.DOUBLE, true),
                column("status", PlatformDataType.STRING, true)
        );
        List<Row> rows = List.of(
                RowFactory.create("A", 1L, 100d, "P"),
                RowFactory.create("A", 2L, null, "X"),
                RowFactory.create("A", 3L, Double.NaN, "P"),
                RowFactory.create("B", 4L, 80d, null),
                RowFactory.create("B", 5L, 70d, "X"),
                RowFactory.create("B", 6L, null, null),
                RowFactory.create("C", 7L, 60d, "Y")
        );
        return table("source", columns, rows);
    }

    private SparkCanvasTable decimalSource() {
        List<CanvasColumnSchema> columns = List.of(new CanvasColumnSchema(
                "amount",
                PlatformDataType.DECIMAL,
                null,
                10,
                2,
                false,
                null,
                false,
                false,
                null
        ));
        return table(
                "source",
                columns,
                List.of(RowFactory.create(new java.math.BigDecimal("1.00")))
        );
    }

    private SparkCanvasTable rankingSource(boolean withTies) {
        List<CanvasColumnSchema> columns = List.of(
                column("group_code", PlatformDataType.STRING, false),
                column("id", PlatformDataType.LONG, false),
                column("score", PlatformDataType.DOUBLE, false)
        );
        List<Row> rows = withTies
                ? List.of(
                        RowFactory.create("A", 1L, 100d),
                        RowFactory.create("A", 2L, 90d),
                        RowFactory.create("A", 3L, 90d),
                        RowFactory.create("B", 4L, 80d),
                        RowFactory.create("B", 5L, 70d),
                        RowFactory.create("B", 6L, 70d)
                )
                : List.of(
                        RowFactory.create("A", 1L, 100d),
                        RowFactory.create("A", 2L, 90d),
                        RowFactory.create("A", 3L, 80d),
                        RowFactory.create("B", 4L, 70d),
                        RowFactory.create("B", 5L, 60d)
                );
        return table("scores", columns, rows);
    }

    private SparkCanvasTable table(
            String name,
            List<CanvasColumnSchema> columns,
            List<Row> rows
    ) {
        CanvasTableSchema schema = new CanvasTableSchema(name, null, columns);
        Dataset<Row> dataset = spark.createDataFrame(
                rows,
                SparkTypeMapper.toStructType(columns)
        );
        return new SparkCanvasTable(schema, dataset);
    }

    private static CanvasColumnSchema column(
            String name,
            PlatformDataType type,
            boolean nullable
    ) {
        return new CanvasColumnSchema(
                name,
                type,
                type == PlatformDataType.STRING ? 128 : null,
                null,
                null,
                nullable,
                null,
                false,
                false,
                null
        );
    }

    private static CanvasLiteral literal(PlatformDataType type, String value) {
        return new CanvasLiteral(type, value);
    }

    private static SortField sort(String name, SortDirection direction) {
        return new SortField(name, direction, NullOrdering.LAST);
    }

    private static CanvasColumnSchema column(
            CanvasNodeOperationResult result,
            String tableName,
            String columnName
    ) {
        return result.propagatedTables().get(tableName).schema().columns().stream()
                .filter(column -> column.name().equals(columnName))
                .findFirst()
                .orElseThrow();
    }

    private static List<Long> ids(
            CanvasNodeOperationResult result,
            String tableName
    ) {
        return result.propagatedTables().get(tableName).dataset()
                .orderBy("id")
                .collectAsList().stream()
                .map(item -> item.<Long>getAs("id"))
                .toList();
    }

    private static Row row(
            CanvasNodeOperationResult result,
            String tableName,
            long id
    ) {
        return result.propagatedTables().get(tableName).dataset()
                .filter("id = " + id)
                .first();
    }

    private static String messageChain(Throwable throwable) {
        StringBuilder message = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null) {
                message.append(current.getMessage()).append('\n');
            }
            current = current.getCause();
        }
        return message.toString();
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
