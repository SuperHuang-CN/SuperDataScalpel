package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.task.canvas.CanvasDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CanvasDefinitionValidatorTest {
    private final CanvasDefinitionUpgrader upgrader = new CanvasDefinitionUpgrader();
    private final CanvasDefinitionValidator validator = new CanvasDefinitionValidator(upgrader);

    @Test
    void acceptsCompleteAndIncompleteModelIdentifiersButRejectsMalformedIdentifiers() {
        String inputId = UUID.randomUUID().toString();
        String outputId = UUID.randomUUID().toString();
        CanvasDefinition valid = definition(inputId, outputId, UUID.randomUUID().toString(), "");
        CanvasDefinition incomplete = definition(inputId, outputId, "", "");
        CanvasDefinition malformedInput = definition(inputId, outputId, "not-a-uuid", "");
        CanvasDefinition malformedOutput = definition(inputId, outputId, "", "not-a-uuid");

        assertDoesNotThrow(() -> validator.validate(valid));
        assertDoesNotThrow(() -> validator.validate(incomplete));
        assertThrows(ResponseStatusException.class, () -> validator.validate(malformedInput));
        assertThrows(ResponseStatusException.class, () -> validator.validate(malformedOutput));
    }

    @Test
    void acceptsLegacyOneDotZeroAndNormalizesItToTheCurrentWriterVersion() {
        CanvasDefinition legacy = new ObjectMapper().readValue(
                "{\"schemaVersion\":1,\"nodes\":[],\"edges\":[]}",
                CanvasDefinition.class
        );

        assertDoesNotThrow(() -> validator.validate(legacy));
        CanvasDefinition upgraded = upgrader.upgradeToCurrent(legacy);

        assertEquals(0, legacy.schemaMinorVersion());
        assertEquals(1, upgraded.schemaVersion());
        assertEquals(5, upgraded.schemaMinorVersion());
    }

    @Test
    void rejectsFutureVersionsAndModelNodesDeclaredAsOneDotZero() {
        CanvasDefinition current = definition(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), "", "");
        CanvasDefinition legacyWithModels = new CanvasDefinition(1, 0, current.nodes(), current.edges());
        CanvasDefinition futureMinor = new CanvasDefinition(1, 6, List.of(), List.of());
        CanvasDefinition futureMajor = new CanvasDefinition(2, 0, List.of(), List.of());

        assertThrows(ResponseStatusException.class, () -> validator.validate(legacyWithModels));
        assertThrows(ResponseStatusException.class, () -> validator.validate(futureMinor));
        assertThrows(ResponseStatusException.class, () -> validator.validate(futureMajor));
    }

    @Test
    void acceptsRenameInOneDotTwoAndRejectsItFromOlderCapabilitySets() {
        CanvasDefinition current = new CanvasDefinition(
                1,
                2,
                List.of(new CanvasDefinition.RenameNodeDefinition(
                        UUID.randomUUID().toString(),
                        "重命名",
                        new CanvasDefinition.CanvasNodeLayout(0, 0, 240, 120),
                        new CanvasDefinition.RenameConfiguration(
                                "orders",
                                "source_orders",
                                List.of(new CanvasDefinition.RenameColumnMapping("id", "order_id"))
                        )
                )),
                List.of()
        );
        CanvasDefinition previous = new CanvasDefinition(1, 1, current.nodes(), current.edges());

        assertDoesNotThrow(() -> validator.validate(current));
        assertThrows(ResponseStatusException.class, () -> validator.validate(previous));
    }

    @Test
    void acceptsKafkaInlineSchemaInOneDotFiveWithoutPersistingAModelReference() {
        CanvasDefinition current = new CanvasDefinition(
                1,
                5,
                List.of(new CanvasDefinition.KafkaInputNodeDefinition(
                        UUID.randomUUID().toString(),
                        "事件输入",
                        new CanvasDefinition.CanvasNodeLayout(0, 0, 240, 120),
                        new CanvasDefinition.KafkaInputConfiguration(
                                UUID.randomUUID().toString(),
                                "order-events",
                                new CanvasDefinition.KafkaValueSchema(List.of(
                                        new CanvasDefinition.KafkaValueColumn(
                                                "event_id",
                                                PlatformDataType.LONG,
                                                null,
                                                null,
                                                null,
                                                false,
                                                "事件 ID"
                                        )
                                )),
                                "order_events",
                                CanvasDefinition.KafkaStartingOffsets.LATEST
                        )
                )),
                List.of()
        );

        assertDoesNotThrow(() -> validator.validate(current));
        assertThrows(
                ResponseStatusException.class,
                () -> validator.validate(new CanvasDefinition(1, 4, current.nodes(), current.edges()))
        );
        String serialized = new ObjectMapper().writeValueAsString(current);
        assertFalse(serialized.contains("valueModelId"));
        assertFalse(serialized.contains("modelId"));
    }

    @Test
    void rejectsGeometryInKafkaInlineSchema() {
        CanvasDefinition definition = new CanvasDefinition(
                1,
                5,
                List.of(new CanvasDefinition.KafkaInputNodeDefinition(
                        UUID.randomUUID().toString(),
                        "空间事件输入",
                        new CanvasDefinition.CanvasNodeLayout(0, 0, 240, 120),
                        new CanvasDefinition.KafkaInputConfiguration(
                                UUID.randomUUID().toString(),
                                "spatial-events",
                                new CanvasDefinition.KafkaValueSchema(List.of(
                                        new CanvasDefinition.KafkaValueColumn(
                                                "shape",
                                                PlatformDataType.GEOMETRY,
                                                null,
                                                null,
                                                null,
                                                true,
                                                null
                                        )
                                )),
                                "spatial_events",
                                CanvasDefinition.KafkaStartingOffsets.LATEST
                        )
                )),
                List.of()
        );

        assertThrows(ResponseStatusException.class, () -> validator.validate(definition));
    }

    private static CanvasDefinition definition(
            String inputId,
            String outputId,
            String inputModelId,
            String outputModelId
    ) {
        return new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(
                        new CanvasDefinition.ModelInputNodeDefinition(
                                inputId,
                                "模型输入",
                                new CanvasDefinition.CanvasNodeLayout(0, 0, 240, 120),
                                new CanvasDefinition.ModelInputConfiguration(inputModelId)
                        ),
                        new CanvasDefinition.ModelOutputNodeDefinition(
                                outputId,
                                "模型输出",
                                new CanvasDefinition.CanvasNodeLayout(320, 0, 240, 120),
                                new CanvasDefinition.ModelOutputConfiguration(
                                        "orders",
                                        outputModelId,
                                        null,
                                        null,
                                        List.of()
                                )
                        )
                ),
                List.of(new CanvasDefinition.CanvasEdgeDefinition(
                        UUID.randomUUID().toString(),
                        inputId,
                        outputId
                ))
        );
    }
}
