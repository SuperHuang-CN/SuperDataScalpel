package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.KafkaValueColumn;
import cn.superhuang.data.scalpel.contract.task.KafkaValueSchema;
import cn.superhuang.data.scalpel.contract.task.KafkaInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.KafkaInputMetadataField;
import cn.superhuang.data.scalpel.contract.task.KafkaInputValueFormat;
import cn.superhuang.data.scalpel.contract.task.KafkaStartingOffsets;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KafkaValueSchemaSupportTest {

    @Test
    void buildsTextAndBinaryPayloadsWithCanonicalMetadataOrder() {
        RecordingIssueSink textIssues = new RecordingIssueSink();
        List<CanvasColumnSchema> textColumns = KafkaValueSchemaSupport.inputColumns(
                input(KafkaInputValueFormat.TEXT, List.of(
                        KafkaInputMetadataField.TIMESTAMP,
                        KafkaInputMetadataField.KEY,
                        KafkaInputMetadataField.OFFSET
                )),
                textIssues,
                "configuration"
        );

        assertFalse(textIssues.hasErrors());
        assertEquals(List.of("value", "_kafka_key", "_kafka_offset", "_kafka_timestamp"),
                textColumns.stream().map(CanvasColumnSchema::name).toList());
        assertEquals(PlatformDataType.STRING, textColumns.getFirst().fieldType());
        assertEquals(PlatformDataType.BINARY, textColumns.get(1).fieldType());

        RecordingIssueSink binaryIssues = new RecordingIssueSink();
        List<CanvasColumnSchema> binaryColumns = KafkaValueSchemaSupport.inputColumns(
                input(KafkaInputValueFormat.BINARY, List.of()),
                binaryIssues,
                "configuration"
        );
        assertFalse(binaryIssues.hasErrors());
        assertEquals(PlatformDataType.BINARY, binaryColumns.getFirst().fieldType());
    }

    @Test
    void rejectsMetadataCollisionsAndDuplicateSelections() {
        RecordingIssueSink issues = new RecordingIssueSink();
        KafkaInputConfiguration configuration = new KafkaInputConfiguration(
                "source", "events",
                new KafkaValueSchema(List.of(new KafkaValueColumn(
                        "_kafka_offset", PlatformDataType.LONG,
                        null, null, null, true, null
                ))),
                "events", KafkaStartingOffsets.LATEST, 10,
                KafkaInputValueFormat.JSON,
                List.of(KafkaInputMetadataField.OFFSET, KafkaInputMetadataField.OFFSET)
        );

        KafkaValueSchemaSupport.inputColumns(configuration, issues, "configuration");

        assertEquals(List.of("KAFKA_METADATA_FIELD_DUPLICATE", "DUPLICATE_COLUMN_NAME"), issues.codes);
    }

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

    private static KafkaInputConfiguration input(
            KafkaInputValueFormat valueFormat,
            List<KafkaInputMetadataField> metadataFields
    ) {
        return new KafkaInputConfiguration(
                "source", "events", new KafkaValueSchema(List.of()), "events",
                KafkaStartingOffsets.LATEST, 10, valueFormat, metadataFields
        );
    }
}
