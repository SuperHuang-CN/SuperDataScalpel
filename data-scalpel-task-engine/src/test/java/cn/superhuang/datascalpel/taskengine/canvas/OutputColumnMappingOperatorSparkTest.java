package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.ColumnMappingMode;
import cn.superhuang.data.scalpel.contract.task.JdbcColumnMapping;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OutputColumnMappingOperatorSparkTest {
    private final OutputColumnMappingOperator operator = new OutputColumnMappingOperator();
    private SparkSession spark;

    @BeforeAll
    void startSpark() {
        spark = SparkSession.builder()
                .master("local[1]")
                .appName("canvas-output-mapping-operator-test")
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
    void castsSafeAndRiskyNumericAssignmentsToTheTargetSparkType() {
        SparkCanvasTable source = source(column("value", PlatformDataType.INTEGER, false));

        RecordingIssueSink wideningIssues = new RecordingIssueSink();
        Dataset<Row> widened = apply(source, column("value", PlatformDataType.LONG, false), wideningIssues);

        assertEquals(DataTypes.LongType, widened.schema().apply("value").dataType());
        assertTrue(wideningIssues.codes.isEmpty());

        RecordingIssueSink narrowingIssues = new RecordingIssueSink();
        Dataset<Row> narrowed = apply(source, column("value", PlatformDataType.SHORT, false), narrowingIssues);

        assertEquals(DataTypes.ShortType, narrowed.schema().apply("value").dataType());
        assertEquals(List.of("COLUMN_CAST_RISK"), narrowingIssues.codes);
        assertFalse(narrowingIssues.hasErrors());

        RecordingIssueSink floatingPointIssues = new RecordingIssueSink();
        Dataset<Row> widenedFloatingPoint = apply(
                source(column("value", PlatformDataType.FLOAT, false)),
                column("value", PlatformDataType.DOUBLE, false),
                floatingPointIssues
        );

        assertEquals(DataTypes.DoubleType, widenedFloatingPoint.schema().apply("value").dataType());
        assertTrue(floatingPointIssues.codes.isEmpty());
    }

    @Test
    void keepsNullabilityLengthAndDecimalNarrowingAsWarnings() {
        SparkCanvasTable stringSource = source(new CanvasColumnSchema(
                "value", PlatformDataType.STRING, 200, null, null,
                true, null, false, false, null
        ));
        RecordingIssueSink stringIssues = new RecordingIssueSink();

        Dataset<Row> selectedString = apply(
                stringSource,
                new CanvasColumnSchema(
                        "value", PlatformDataType.STRING, 20, null, null,
                        false, null, false, false, null
                ),
                stringIssues
        );

        assertEquals(DataTypes.StringType, selectedString.schema().apply("value").dataType());
        assertEquals(List.of("NULLABILITY_RISK", "STRING_LENGTH_RISK"), stringIssues.codes);
        assertFalse(stringIssues.hasErrors());

        SparkCanvasTable decimalSource = source(new CanvasColumnSchema(
                "value", PlatformDataType.DECIMAL, null, 20, 4,
                false, null, false, false, null
        ));
        RecordingIssueSink decimalIssues = new RecordingIssueSink();

        Dataset<Row> selectedDecimal = apply(
                decimalSource,
                new CanvasColumnSchema(
                        "value", PlatformDataType.DECIMAL, null, 10, 2,
                        false, null, false, false, null
                ),
                decimalIssues
        );

        assertEquals(DataTypes.createDecimalType(10, 2), selectedDecimal.schema().apply("value").dataType());
        assertEquals(List.of("DECIMAL_PRECISION_RISK"), decimalIssues.codes);
        assertFalse(decimalIssues.hasErrors());
    }

    @Test
    void rejectsAConversionThatSparkCannotAnalyze() {
        SparkCanvasTable source = source(column("value", PlatformDataType.BINARY, false));
        RecordingIssueSink issues = new RecordingIssueSink();

        Dataset<Row> selected = apply(source, column("value", PlatformDataType.DATE, false), issues);

        assertNull(selected);
        assertTrue(issues.hasErrors());
        assertEquals(List.of("UNSUPPORTED_COLUMN_CAST"), issues.codes);
    }

    @Test
    void keepsTheSysUserIntegerToLongAndShortScenarioValidWithOnlyNarrowingWarnings() {
        List<CanvasColumnSchema> sourceColumns = List.of(
                column("id", PlatformDataType.INTEGER, false),
                column("dept_id", PlatformDataType.INTEGER, true),
                column("login_flag", PlatformDataType.INTEGER, true),
                column("del_flag", PlatformDataType.INTEGER, true),
                column("tenant_id", PlatformDataType.INTEGER, true),
                column("age", PlatformDataType.INTEGER, true)
        );
        List<CanvasColumnSchema> targetColumns = List.of(
                column("id", PlatformDataType.LONG, false),
                column("dept_id", PlatformDataType.LONG, true),
                column("login_flag", PlatformDataType.SHORT, true),
                column("del_flag", PlatformDataType.SHORT, true),
                column("tenant_id", PlatformDataType.LONG, true),
                column("age", PlatformDataType.SHORT, true)
        );
        RecordingIssueSink issues = new RecordingIssueSink();

        Dataset<Row> selected = operator.apply(
                source(sourceColumns),
                new CanvasTableSchema("target", null, targetColumns),
                ColumnMappingMode.BY_NAME,
                List.of(),
                issues
        );

        assertFalse(issues.hasErrors());
        assertEquals(
                List.of("COLUMN_CAST_RISK", "COLUMN_CAST_RISK", "COLUMN_CAST_RISK"),
                issues.codes
        );
        assertEquals(DataTypes.LongType, selected.schema().apply("id").dataType());
        assertEquals(DataTypes.LongType, selected.schema().apply("dept_id").dataType());
        assertEquals(DataTypes.ShortType, selected.schema().apply("login_flag").dataType());
        assertEquals(DataTypes.ShortType, selected.schema().apply("del_flag").dataType());
        assertEquals(DataTypes.LongType, selected.schema().apply("tenant_id").dataType());
        assertEquals(DataTypes.ShortType, selected.schema().apply("age").dataType());
    }

    @Test
    void mapsExplicitColumnsAndCastsThemToTheTargetSchema() {
        SparkCanvasTable source = source(List.of(
                column("source_id", PlatformDataType.INTEGER, false),
                column("description", PlatformDataType.STRING, true)
        ));
        CanvasTableSchema target = new CanvasTableSchema("target", null, List.of(
                column("id", PlatformDataType.LONG, false),
                column("description", PlatformDataType.STRING, true)
        ));
        RecordingIssueSink issues = new RecordingIssueSink();

        Dataset<Row> selected = operator.apply(
                source,
                target,
                ColumnMappingMode.EXPLICIT,
                List.of(
                        new JdbcColumnMapping("source_id", "id"),
                        new JdbcColumnMapping("description", "description")
                ),
                issues
        );

        assertFalse(issues.hasErrors());
        assertTrue(issues.codes.isEmpty());
        assertEquals(List.of("id", "description"), List.of(selected.columns()));
        assertEquals(DataTypes.LongType, selected.schema().apply("id").dataType());
    }

    @Test
    void preservesAKafkaKeyFromTheSourceInTheSameProjectionAsExplicitValueMapping() {
        SparkCanvasTable source = source(List.of(
                column("business_key", PlatformDataType.LONG, false),
                column("source_name", PlatformDataType.STRING, false)
        ));
        CanvasTableSchema target = new CanvasTableSchema("target", null, List.of(
                column("name", PlatformDataType.STRING, false)
        ));
        RecordingIssueSink issues = new RecordingIssueSink();

        Dataset<Row> selected = operator.applyPreserving(
                source,
                target,
                ColumnMappingMode.EXPLICIT,
                List.of(new JdbcColumnMapping("source_name", "name")),
                "business_key",
                "__datascalpel_kafka_key",
                issues
        );

        assertFalse(issues.hasErrors());
        assertEquals(
                List.of("name", "__datascalpel_kafka_key"),
                List.of(selected.columns())
        );
        assertEquals(DataTypes.StringType, selected.schema().apply("__datascalpel_kafka_key").dataType());
    }

    @Test
    void validatesByNameAndExplicitMappingBusinessRules() {
        SparkCanvasTable source = source(List.of(
                column("id", PlatformDataType.LONG, false),
                column("extra", PlatformDataType.STRING, true)
        ));
        CanvasTableSchema target = new CanvasTableSchema("target", null, List.of(
                column("id", PlatformDataType.LONG, false),
                column("required_name", PlatformDataType.STRING, false)
        ));

        RecordingIssueSink byNameIssues = new RecordingIssueSink();
        Dataset<Row> byName = operator.apply(
                source,
                target,
                ColumnMappingMode.BY_NAME,
                List.of(new JdbcColumnMapping("id", "id")),
                byNameIssues
        );

        assertNull(byName);
        assertTrue(byNameIssues.hasErrors());
        assertTrue(byNameIssues.codes.contains("INVALID_COLUMN_MAPPING"));
        assertTrue(byNameIssues.codes.contains("REQUIRED_TARGET_COLUMN_MISSING"));
        assertTrue(byNameIssues.codes.contains("SOURCE_COLUMN_IGNORED"));

        RecordingIssueSink explicitIssues = new RecordingIssueSink();
        Dataset<Row> explicit = operator.apply(
                source,
                target,
                ColumnMappingMode.EXPLICIT,
                List.of(
                        new JdbcColumnMapping("id", "id"),
                        new JdbcColumnMapping("id", "id")
                ),
                explicitIssues
        );

        assertNull(explicit);
        assertTrue(explicitIssues.hasErrors());
        assertTrue(explicitIssues.codes.contains("DUPLICATE_TARGET_COLUMN_MAPPING"));
        assertTrue(explicitIssues.codes.contains("REQUIRED_TARGET_COLUMN_MISSING"));
    }

    private Dataset<Row> apply(
            SparkCanvasTable source,
            CanvasColumnSchema targetColumn,
            RecordingIssueSink issues
    ) {
        return operator.apply(
                source,
                new CanvasTableSchema("target", null, List.of(targetColumn)),
                ColumnMappingMode.BY_NAME,
                List.of(),
                issues
        );
    }

    private SparkCanvasTable source(CanvasColumnSchema column) {
        return source(List.of(column));
    }

    private SparkCanvasTable source(List<CanvasColumnSchema> columns) {
        CanvasTableSchema schema = new CanvasTableSchema("source", null, columns);
        Dataset<Row> dataset = spark.createDataFrame(
                List.<Row>of(),
                SparkTypeMapper.toStructType(schema.columns())
        );
        return new SparkCanvasTable(schema, dataset);
    }

    private static CanvasColumnSchema column(
            String name,
            PlatformDataType type,
            boolean nullable
    ) {
        return new CanvasColumnSchema(
                name, type, null, null, null,
                nullable, null, false, false, null
        );
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
