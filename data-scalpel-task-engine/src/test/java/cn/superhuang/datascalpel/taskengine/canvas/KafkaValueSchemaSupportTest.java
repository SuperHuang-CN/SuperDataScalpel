package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.KafkaValueColumn;
import cn.superhuang.data.scalpel.contract.task.KafkaValueSchema;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KafkaValueSchemaSupportTest {

    @Test
    void convertsTheInlineSchemaWithoutResolvingAModel() {
        RecordingIssueSink issues = new RecordingIssueSink();

        List<CanvasColumnSchema> columns = KafkaValueSchemaSupport.columns(
                new KafkaValueSchema(List.of(
                        new KafkaValueColumn(
                                "event_id",
                                PlatformDataType.LONG,
                                null,
                                null,
                                null,
                                false,
                                "事件 ID"
                        ),
                        new KafkaValueColumn(
                                "amount",
                                PlatformDataType.DECIMAL,
                                null,
                                18,
                                2,
                                true,
                                null
                        )
                )),
                issues,
                "configuration.valueSchema"
        );

        assertFalse(issues.hasErrors());
        assertEquals(List.of("event_id", "amount"), columns.stream().map(CanvasColumnSchema::name).toList());
        assertEquals(PlatformDataType.DECIMAL, columns.get(1).fieldType());
        assertEquals(18, columns.get(1).precision());
        assertEquals(2, columns.get(1).scale());
    }

    @Test
    void rejectsEmptyDuplicateAndInvalidParameterizedFields() {
        RecordingIssueSink emptyIssues = new RecordingIssueSink();
        assertTrue(KafkaValueSchemaSupport.columns(
                new KafkaValueSchema(List.of()),
                emptyIssues,
                "configuration.valueSchema"
        ).isEmpty());
        assertEquals(List.of("KAFKA_VALUE_SCHEMA_EMPTY"), emptyIssues.codes);

        RecordingIssueSink invalidIssues = new RecordingIssueSink();
        List<CanvasColumnSchema> columns = KafkaValueSchemaSupport.columns(
                new KafkaValueSchema(List.of(
                        new KafkaValueColumn("id", PlatformDataType.STRING, 64, null, null, false, null),
                        new KafkaValueColumn("id", PlatformDataType.LONG, null, null, null, false, null),
                        new KafkaValueColumn("amount", PlatformDataType.DECIMAL, null, 2, 3, true, null)
                )),
                invalidIssues,
                "configuration.valueSchema"
        );

        assertTrue(invalidIssues.hasErrors());
        assertEquals(List.of("id"), columns.stream().map(CanvasColumnSchema::name).toList());
        assertEquals(
                List.of("DUPLICATE_COLUMN_NAME", "KAFKA_VALUE_SCHEMA_INVALID"),
                invalidIssues.codes
        );
    }

    @Test
    void rejectsGeometryWithAStableSpatialError() {
        RecordingIssueSink issues = new RecordingIssueSink();

        List<CanvasColumnSchema> columns = KafkaValueSchemaSupport.columns(
                new KafkaValueSchema(List.of(
                        new KafkaValueColumn(
                                "shape",
                                PlatformDataType.GEOMETRY,
                                null,
                                null,
                                null,
                                true,
                                null
                        )
                )),
                issues,
                "configuration.valueSchema"
        );

        assertTrue(columns.isEmpty());
        assertEquals(List.of("SPATIAL_FIELD_UNSUPPORTED"), issues.codes);
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
