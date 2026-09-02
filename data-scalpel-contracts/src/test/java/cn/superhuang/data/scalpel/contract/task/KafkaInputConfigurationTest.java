package cn.superhuang.data.scalpel.contract.task;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KafkaInputConfigurationTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void roundTripsFormatsAndDefensivelyCopiesMetadataFields() throws Exception {
        List<KafkaInputMetadataField> metadataFields = new ArrayList<>(List.of(
                KafkaInputMetadataField.KEY,
                KafkaInputMetadataField.OFFSET
        ));
        KafkaInputConfiguration configuration = new KafkaInputConfiguration(
                "source", "events",
                new KafkaValueSchema(List.of(new KafkaValueColumn(
                        "id", PlatformDataType.LONG, null, null, null, false, null
                ))),
                "events", KafkaStartingOffsets.LATEST, 10,
                KafkaInputValueFormat.JSON, metadataFields
        );

        metadataFields.clear();

        assertEquals(List.of(KafkaInputMetadataField.KEY, KafkaInputMetadataField.OFFSET),
                configuration.metadataFields());
        assertEquals(configuration, objectMapper.readValue(
                objectMapper.writeValueAsString(configuration), KafkaInputConfiguration.class
        ));
    }

    @Test
    void keepsLegacyConfigurationDefaultsAndRejectsUnknownEnums() throws Exception {
        KafkaInputConfiguration legacy = objectMapper.readValue(
                """
                {
                  "dataSourceId": "source",
                  "topic": "events",
                  "valueSchema": {"columns": []},
                  "outputTableName": "events",
                  "startingOffsets": "LATEST",
                  "triggerIntervalSeconds": 10
                }
                """,
                KafkaInputConfiguration.class
        );

        assertEquals(KafkaInputValueFormat.JSON, legacy.effectiveValueFormat());
        assertEquals(List.of(), legacy.effectiveMetadataFields());
        assertThrows(Exception.class, () -> objectMapper.readValue(
                """
                {
                  "dataSourceId": "source",
                  "topic": "events",
                  "valueSchema": {"columns": []},
                  "outputTableName": "events",
                  "startingOffsets": "LATEST",
                  "triggerIntervalSeconds": 10,
                  "valueFormat": "CSV",
                  "metadataFields": []
                }
                """,
                KafkaInputConfiguration.class
        ));
    }
}
