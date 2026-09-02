package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.CastFailureStrategy;
import cn.superhuang.data.scalpel.contract.task.ColumnTypeCast;
import cn.superhuang.data.scalpel.contract.task.EpochTimestampUnit;
import cn.superhuang.data.scalpel.contract.task.MetadataSnapshot;
import cn.superhuang.data.scalpel.contract.task.ProcessorOutput;
import cn.superhuang.data.scalpel.contract.task.StringTemporalParseOptions;
import cn.superhuang.data.scalpel.contract.task.StringTimestampZoneMode;
import cn.superhuang.data.scalpel.contract.task.TemporalStringFormatOptions;
import cn.superhuang.data.scalpel.contract.task.TypeCastConfiguration;
import cn.superhuang.data.scalpel.contract.task.TypeCastNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.TypeCastOperation;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TypeCastEpochTimestampSparkTest {
    private static final CanvasNodeLayout LAYOUT = new CanvasNodeLayout(0d, 0d, 240d, 120d);
    private static final PlatformTypeDefinition TIMESTAMP = new PlatformTypeDefinition(
            PlatformDataType.TIMESTAMP, null, null, null, null
    );
    private static final PlatformTypeDefinition DATE = new PlatformTypeDefinition(
            PlatformDataType.DATE, null, null, null, null
    );
    private static final PlatformTypeDefinition STRING = new PlatformTypeDefinition(
            PlatformDataType.STRING, null, null, null, null
    );
    private static final PlatformTypeDefinition LONG = new PlatformTypeDefinition(
            PlatformDataType.LONG, null, null, null, null
    );

    private final TypeCastNodeOperator operator = new TypeCastNodeOperator();
    private SparkSession spark;

    @BeforeAll
    void startSpark() {
        spark = SparkSession.builder()
                .master("local[1]")
                .appName("canvas-type-cast-epoch-timestamp-test")
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
        if (spark != null) spark.stop();
    }

    @Test
    void convertsSecondsMillisecondsAndMicrosecondsWithoutLosingPrecision() {
        SparkCanvasTable source = table(
                "events",
                List.of(column("seconds", false), column("milliseconds", false), column("microseconds", false)),
                List.of(
                        RowFactory.create(-1L, 123L, 123_456L),
                        RowFactory.create(1_704_067_200L, 1_704_067_200_123L, 1_704_067_200_123_456L)
                )
        );
        CanvasNodeOperationResult result = apply(
                source,
                List.of(
                        cast("seconds", CastFailureStrategy.FAIL, EpochTimestampUnit.SECONDS),
                        cast("milliseconds", CastFailureStrategy.FAIL, EpochTimestampUnit.MILLISECONDS),
                        cast("microseconds", CastFailureStrategy.FAIL, EpochTimestampUnit.MICROSECONDS)
                ),
                new RecordingIssueSink()
        );

        List<Row> rows = result.propagatedTables().get("converted").dataset().collectAsList();
        assertEquals(Instant.ofEpochSecond(-1), rows.getFirst().getTimestamp(0).toInstant());
        assertEquals(Instant.ofEpochMilli(123L), rows.getFirst().getTimestamp(1).toInstant());
        assertEquals(Instant.ofEpochSecond(0, 123_456_000L), rows.getFirst().getTimestamp(2).toInstant());
        assertEquals(Instant.parse("2024-01-01T00:00:00Z"), rows.get(1).getTimestamp(0).toInstant());
        assertEquals(Instant.parse("2024-01-01T00:00:00.123Z"), rows.get(1).getTimestamp(1).toInstant());
        assertEquals(Instant.parse("2024-01-01T00:00:00.123456Z"), rows.get(1).getTimestamp(2).toInstant());
    }

    @Test
    void keepsLegacySecondsCastAndPropagatesNulls() {
        SparkCanvasTable source = table(
                "events",
                List.of(column("event_time", true)),
                List.of(RowFactory.create(1L), RowFactory.create((Object) null))
        );
        CanvasNodeOperationResult result = apply(
                source,
                List.of(new ColumnTypeCast("event_time", TIMESTAMP, CastFailureStrategy.FAIL)),
                new RecordingIssueSink()
        );

        List<Row> rows = result.propagatedTables().get("converted").dataset().collectAsList();
        assertEquals(Instant.ofEpochSecond(1), rows.getFirst().getTimestamp(0).toInstant());
        assertNull(rows.get(1).get(0));
    }

    @Test
    void turnsOverflowIntoNullOnlyForSetNull() {
        SparkCanvasTable source = table(
                "events",
                List.of(column("event_time", false)),
                List.of(RowFactory.create(Long.MAX_VALUE))
        );
        CanvasNodeOperationResult setNullResult = apply(
                source,
                List.of(cast("event_time", CastFailureStrategy.SET_NULL, EpochTimestampUnit.MILLISECONDS)),
                new RecordingIssueSink()
        );
        assertNull(setNullResult.propagatedTables().get("converted").dataset().collectAsList().getFirst().get(0));

        CanvasNodeOperationResult failResult = apply(
                source,
                List.of(cast("event_time", CastFailureStrategy.FAIL, EpochTimestampUnit.MILLISECONDS)),
                new RecordingIssueSink()
        );
        assertThrows(ArithmeticException.class, () -> failResult.propagatedTables()
                .get("converted").dataset().collectAsList());
    }

    @Test
    void rejectsEpochUnitForUnsupportedSourceOrTarget() {
        SparkCanvasTable source = table(
                "events",
                List.of(column("event_time", false, PlatformDataType.INTEGER)),
                List.of(RowFactory.create(123))
        );
        RecordingIssueSink issues = new RecordingIssueSink();
        CanvasNodeOperationResult result = apply(
                source,
                List.of(cast("event_time", CastFailureStrategy.FAIL, EpochTimestampUnit.MILLISECONDS)),
                issues
        );

        assertTrue(issues.codes.contains("EPOCH_TIMESTAMP_UNIT_SOURCE_TYPE_UNSUPPORTED"));
        assertTrue(result.propagatedTables().isEmpty());
    }

    @Test
    void parsesStringDatesAndTimestampsWithExplicitTimezoneSemantics() {
        SparkCanvasTable source = table(
                "events",
                List.of(
                        column("date_text", true, PlatformDataType.STRING),
                        column("local_time_text", true, PlatformDataType.STRING),
                        column("offset_time_text", true, PlatformDataType.STRING)
                ),
                List.of(RowFactory.create(
                        "2024/02/29",
                        "2024-01-01 08:00:00.123",
                        "2024-01-01T08:00:00+08:00"
                ))
        );

        CanvasNodeOperationResult result = apply(
                source,
                List.of(
                        stringTemporalCast(
                                "date_text", DATE, CastFailureStrategy.FAIL,
                                "yyyy/MM/dd", null, null
                        ),
                        stringTemporalCast(
                                "local_time_text", TIMESTAMP, CastFailureStrategy.FAIL,
                                "yyyy-MM-dd HH:mm:ss.SSS",
                                StringTimestampZoneMode.SOURCE_TIME_ZONE, "Asia/Shanghai"
                        ),
                        stringTemporalCast(
                                "offset_time_text", TIMESTAMP, CastFailureStrategy.FAIL,
                                "yyyy-MM-dd'T'HH:mm:ssXXX",
                                StringTimestampZoneMode.EMBEDDED_OFFSET, null
                        )
                ),
                new RecordingIssueSink()
        );

        Row row = result.propagatedTables().get("converted").dataset().collectAsList().getFirst();
        assertEquals(java.sql.Date.valueOf("2024-02-29"), row.getDate(0));
        assertEquals(Instant.parse("2024-01-01T00:00:00.123Z"), row.getTimestamp(1).toInstant());
        assertEquals(Instant.parse("2024-01-01T00:00:00Z"), row.getTimestamp(2).toInstant());
    }

    @Test
    void setsInvalidStringDatesToNullWhenConfigured() {
        SparkCanvasTable source = table(
                "events",
                List.of(column("date_text", true, PlatformDataType.STRING)),
                List.of(RowFactory.create("2024-02-30"), RowFactory.create((Object) null))
        );

        CanvasNodeOperationResult result = apply(
                source,
                List.of(stringTemporalCast(
                        "date_text", DATE, CastFailureStrategy.SET_NULL, "yyyy-MM-dd", null, null
                )),
                new RecordingIssueSink()
        );

        List<Row> rows = result.propagatedTables().get("converted").dataset().collectAsList();
        assertNull(rows.get(0).get(0));
        assertNull(rows.get(1).get(0));
    }

    @Test
    void rejectsIncompatibleStringTemporalConfigurations() {
        SparkCanvasTable source = table(
                "events",
                List.of(column("event_time", false, PlatformDataType.LONG)),
                List.of(RowFactory.create(1L))
        );
        RecordingIssueSink issues = new RecordingIssueSink();

        CanvasNodeOperationResult result = apply(
                source,
                List.of(stringTemporalCast(
                        "event_time", TIMESTAMP, CastFailureStrategy.FAIL,
                        "yyyy-MM-dd HH:mm:ss", StringTimestampZoneMode.SOURCE_TIME_ZONE, "Mars/Olympus"
                )),
                issues
        );

        assertTrue(issues.codes.contains("STRING_TEMPORAL_PARSE_SOURCE_TYPE_UNSUPPORTED"));
        assertTrue(issues.codes.contains("STRING_TEMPORAL_PARSE_SOURCE_ZONE_INVALID"));
        assertTrue(result.propagatedTables().isEmpty());
    }

    @Test
    void formatsDateTimestampAndTimestampNtzWithExplicitTimezoneSemantics() {
        SparkCanvasTable source = table(
                "events",
                List.of(
                        column("business_date", true, PlatformDataType.DATE),
                        column("submitted_at", true, PlatformDataType.TIMESTAMP),
                        column("local_clock", true, PlatformDataType.TIMESTAMP_NTZ)
                ),
                List.of(
                        RowFactory.create(
                                java.sql.Date.valueOf("2024-02-29"),
                                java.sql.Timestamp.from(Instant.parse("2024-01-01T00:00:00.123456Z")),
                                LocalDateTime.parse("2024-01-01T08:00:00.123456")
                        ),
                        RowFactory.create(null, null, null)
                )
        );

        CanvasNodeOperationResult result = apply(
                source,
                List.of(
                        temporalStringCast("business_date", "yyyy/MM/dd", null),
                        temporalStringCast(
                                "submitted_at", "yyyy-MM-dd HH:mm:ss.SSSSSS", "Asia/Shanghai"
                        ),
                        temporalStringCast("local_clock", "yyyy/MM/dd HH:mm:ss.SSSSSS", null)
                ),
                new RecordingIssueSink()
        );

        List<Row> rows = result.propagatedTables().get("converted").dataset().collectAsList();
        assertEquals("2024/02/29", rows.getFirst().getString(0));
        assertEquals("2024-01-01 08:00:00.123456", rows.getFirst().getString(1));
        assertEquals("2024/01/01 08:00:00.123456", rows.getFirst().getString(2));
        assertNull(rows.get(1).get(0));
        assertNull(rows.get(1).get(1));
        assertNull(rows.get(1).get(2));
    }

    @Test
    void convertsDateAndTimestampToEpochLongInConfiguredUnits() {
        java.sql.Date date = java.sql.Date.valueOf("1970-01-02");
        java.sql.Timestamp timestamp = java.sql.Timestamp.from(
                Instant.parse("2024-01-01T00:00:00.123456Z")
        );
        SparkCanvasTable source = table(
                "events",
                List.of(
                        column("date_seconds", true, PlatformDataType.DATE),
                        column("date_millis", true, PlatformDataType.DATE),
                        column("date_micros", true, PlatformDataType.DATE),
                        column("timestamp_seconds", true, PlatformDataType.TIMESTAMP),
                        column("timestamp_millis", true, PlatformDataType.TIMESTAMP),
                        column("timestamp_micros", true, PlatformDataType.TIMESTAMP)
                ),
                List.of(
                        RowFactory.create(date, date, date, timestamp, timestamp, timestamp),
                        RowFactory.create(null, null, null, null, null, null)
                )
        );

        CanvasNodeOperationResult result = apply(
                source,
                List.of(
                        temporalEpochCast("date_seconds", EpochTimestampUnit.SECONDS),
                        temporalEpochCast("date_millis", EpochTimestampUnit.MILLISECONDS),
                        temporalEpochCast("date_micros", EpochTimestampUnit.MICROSECONDS),
                        temporalEpochCast("timestamp_seconds", EpochTimestampUnit.SECONDS),
                        temporalEpochCast("timestamp_millis", EpochTimestampUnit.MILLISECONDS),
                        temporalEpochCast("timestamp_micros", EpochTimestampUnit.MICROSECONDS)
                ),
                new RecordingIssueSink()
        );

        List<Row> rows = result.propagatedTables().get("converted").dataset().collectAsList();
        Row value = rows.getFirst();
        assertEquals(86_400L, value.getLong(0));
        assertEquals(86_400_000L, value.getLong(1));
        assertEquals(86_400_000_000L, value.getLong(2));
        assertEquals(1_704_067_200L, value.getLong(3));
        assertEquals(1_704_067_200_123L, value.getLong(4));
        assertEquals(1_704_067_200_123_456L, value.getLong(5));
        for (int index = 0; index < 6; index++) assertNull(rows.get(1).get(index));
    }

    private CanvasNodeOperationResult apply(
            SparkCanvasTable source,
            List<ColumnTypeCast> casts,
            RecordingIssueSink issues
    ) {
        TypeCastNodeDefinition definition = new TypeCastNodeDefinition(
                UUID.randomUUID().toString(),
                "类型转换",
                LAYOUT,
                new TypeCastConfiguration(List.of(new TypeCastOperation(
                        UUID.randomUUID().toString(),
                        source.name(),
                        new ProcessorOutput.CreateNewTable("converted"),
                        casts
                )))
        );
        return operator.apply(
                definition,
                Map.of(source.name(), source),
                new CanvasNodeOperationContext(
                        spark,
                        MetadataIndex.create(new MetadataSnapshot(List.of(), List.of())),
                        issues,
                        new SchemaOnlyCanvasNodeDataAccess(spark)
                )
        );
    }

    private SparkCanvasTable table(
            String name,
            List<CanvasColumnSchema> columns,
            List<Row> rows
    ) {
        Dataset<Row> dataset = spark.createDataFrame(rows, SparkTypeMapper.toStructType(columns));
        return new SparkCanvasTable(
                new CanvasTableSchema(name, null, columns, CanvasDatasetKind.BOUNDED, null, null),
                dataset
        );
    }

    private static ColumnTypeCast cast(
            String columnName,
            CastFailureStrategy failureStrategy,
            EpochTimestampUnit unit
    ) {
        return new ColumnTypeCast(columnName, TIMESTAMP, failureStrategy, unit);
    }

    private static ColumnTypeCast stringTemporalCast(
            String columnName,
            PlatformTypeDefinition targetType,
            CastFailureStrategy failureStrategy,
            String pattern,
            StringTimestampZoneMode zoneMode,
            String sourceTimeZone
    ) {
        return new ColumnTypeCast(
                columnName,
                targetType,
                failureStrategy,
                null,
                new StringTemporalParseOptions(pattern, zoneMode, sourceTimeZone)
        );
    }

    private static ColumnTypeCast temporalStringCast(
            String columnName,
            String pattern,
            String targetTimeZone
    ) {
        return new ColumnTypeCast(
                columnName,
                STRING,
                CastFailureStrategy.FAIL,
                null,
                null,
                new TemporalStringFormatOptions(pattern, targetTimeZone)
        );
    }

    private static ColumnTypeCast temporalEpochCast(
            String columnName,
            EpochTimestampUnit unit
    ) {
        return new ColumnTypeCast(columnName, LONG, CastFailureStrategy.FAIL, unit);
    }

    private static CanvasColumnSchema column(String name, boolean nullable) {
        return column(name, nullable, PlatformDataType.LONG);
    }

    private static CanvasColumnSchema column(String name, boolean nullable, PlatformDataType type) {
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
