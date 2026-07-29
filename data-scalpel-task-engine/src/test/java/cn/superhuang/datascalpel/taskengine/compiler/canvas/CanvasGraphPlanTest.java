package cn.superhuang.datascalpel.taskengine.compiler.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasEdgeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout;
import cn.superhuang.data.scalpel.contract.task.ColumnMappingMode;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputConfiguration;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcWriteMode;
import cn.superhuang.data.scalpel.contract.task.KafkaInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.KafkaInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.KafkaStartingOffsets;
import cn.superhuang.data.scalpel.contract.task.KafkaValueSchema;
import cn.superhuang.data.scalpel.contract.task.ModelInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.ModelInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ModelOutputConfiguration;
import cn.superhuang.data.scalpel.contract.task.ModelOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.RenameConfiguration;
import cn.superhuang.data.scalpel.contract.task.RenameNodeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanvasGraphPlanTest {

    @Test
    void appliesInputAndOutputDegreeRulesToModelNodes() {
        String inputId = UUID.randomUUID().toString();
        String outputId = UUID.randomUUID().toString();
        CanvasDefinition validTopology = new CanvasDefinition(
                1,
                1,
                List.of(
                        new ModelInputNodeDefinition(
                                inputId,
                                "模型输入",
                                new CanvasNodeLayout(0d, 0d, 240d, 120d),
                                new ModelInputConfiguration(UUID.randomUUID())
                        ),
                        new ModelOutputNodeDefinition(
                                outputId,
                                "模型输出",
                                new CanvasNodeLayout(320d, 0d, 240d, 120d),
                                new ModelOutputConfiguration("orders", UUID.randomUUID(), null, null, List.of())
                        )
                ),
                List.of(new CanvasEdgeDefinition(UUID.randomUUID().toString(), inputId, outputId))
        );

        CanvasGraphPlan validPlan = CanvasGraphPlan.create(validTopology);
        assertFalse(hasDegreeIssue(validPlan, 0));
        assertFalse(hasDegreeIssue(validPlan, 1));

        CanvasGraphPlan invalidPlan = CanvasGraphPlan.create(new CanvasDefinition(
                1,
                1,
                validTopology.nodes(),
                List.of(new CanvasEdgeDefinition(UUID.randomUUID().toString(), outputId, inputId))
        ));
        assertTrue(hasDegreeIssue(invalidPlan, 0));
        assertTrue(hasDegreeIssue(invalidPlan, 1));
    }

    @Test
    void acceptsSupportedMinorVersionsButGatesNodeCapabilitiesAndFutureVersions() {
        CanvasGraphPlan legacy = CanvasGraphPlan.create(new CanvasDefinition(1, null, List.of(), List.of()));
        CanvasGraphPlan previous = CanvasGraphPlan.create(new CanvasDefinition(1, 1, List.of(), List.of()));
        CanvasGraphPlan previousRename = CanvasGraphPlan.create(new CanvasDefinition(1, 2, List.of(), List.of()));
        CanvasGraphPlan previousStreaming = CanvasGraphPlan.create(new CanvasDefinition(1, 3, List.of(), List.of()));
        CanvasGraphPlan previousFileInput = CanvasGraphPlan.create(new CanvasDefinition(1, 4, List.of(), List.of()));
        CanvasGraphPlan previousKafka = CanvasGraphPlan.create(new CanvasDefinition(1, 5, List.of(), List.of()));
        CanvasGraphPlan current = CanvasGraphPlan.create(new CanvasDefinition(1, 6, List.of(), List.of()));
        CanvasGraphPlan futureMinor = CanvasGraphPlan.create(new CanvasDefinition(1, 7, List.of(), List.of()));
        CanvasGraphPlan futureMajor = CanvasGraphPlan.create(new CanvasDefinition(2, 0, List.of(), List.of()));

        assertFalse(hasCanvasIssue(legacy, "UNSUPPORTED_SCHEMA_VERSION"));
        assertFalse(hasCanvasIssue(legacy, "UNSUPPORTED_SCHEMA_MINOR_VERSION"));
        assertFalse(hasCanvasIssue(previous, "UNSUPPORTED_SCHEMA_VERSION"));
        assertFalse(hasCanvasIssue(previous, "UNSUPPORTED_SCHEMA_MINOR_VERSION"));
        assertFalse(hasCanvasIssue(previousRename, "UNSUPPORTED_SCHEMA_VERSION"));
        assertFalse(hasCanvasIssue(previousRename, "UNSUPPORTED_SCHEMA_MINOR_VERSION"));
        assertFalse(hasCanvasIssue(previousStreaming, "UNSUPPORTED_SCHEMA_VERSION"));
        assertFalse(hasCanvasIssue(previousStreaming, "UNSUPPORTED_SCHEMA_MINOR_VERSION"));
        assertFalse(hasCanvasIssue(previousFileInput, "UNSUPPORTED_SCHEMA_VERSION"));
        assertFalse(hasCanvasIssue(previousFileInput, "UNSUPPORTED_SCHEMA_MINOR_VERSION"));
        assertFalse(hasCanvasIssue(previousKafka, "UNSUPPORTED_SCHEMA_MINOR_VERSION"));
        assertFalse(hasCanvasIssue(current, "UNSUPPORTED_SCHEMA_VERSION"));
        assertFalse(hasCanvasIssue(current, "UNSUPPORTED_SCHEMA_MINOR_VERSION"));
        assertTrue(hasCanvasIssue(futureMinor, "UNSUPPORTED_SCHEMA_MINOR_VERSION"));
        assertTrue(hasCanvasIssue(futureMajor, "UNSUPPORTED_SCHEMA_VERSION"));

        String inputId = UUID.randomUUID().toString();
        CanvasGraphPlan legacyWithModel = CanvasGraphPlan.create(new CanvasDefinition(
                1,
                0,
                List.of(new ModelInputNodeDefinition(
                        inputId,
                        "模型输入",
                        new CanvasNodeLayout(0d, 0d, 240d, 120d),
                        new ModelInputConfiguration(UUID.randomUUID())
                )),
                List.of()
        ));
        assertTrue(legacyWithModel.entries().getFirst().result().result().issues().stream()
                .anyMatch(issue -> issue.code().equals("NODE_TYPE_REQUIRES_SCHEMA_VERSION")));

        CanvasGraphPlan previousWithRename = CanvasGraphPlan.create(new CanvasDefinition(
                1,
                1,
                List.of(new RenameNodeDefinition(
                        UUID.randomUUID().toString(),
                        "重命名",
                        new CanvasNodeLayout(0d, 0d, 240d, 120d),
                        new RenameConfiguration("orders", "source_orders", List.of())
                )),
                List.of()
        ));
        assertTrue(previousWithRename.entries().getFirst().result().result().issues().stream()
                .anyMatch(issue -> issue.code().equals("NODE_TYPE_REQUIRES_SCHEMA_VERSION")));
    }

    @Test
    void appliesOneInputAndAtLeastOneOutputDegreeRuleToRename() {
        String inputId = UUID.randomUUID().toString();
        String renameId = UUID.randomUUID().toString();
        String outputId = UUID.randomUUID().toString();
        CanvasDefinition definition = new CanvasDefinition(
                1,
                2,
                List.of(
                        new ModelInputNodeDefinition(
                                inputId,
                                "模型输入",
                                new CanvasNodeLayout(0d, 0d, 240d, 120d),
                                new ModelInputConfiguration(UUID.randomUUID())
                        ),
                        new RenameNodeDefinition(
                                renameId,
                                "重命名",
                                new CanvasNodeLayout(320d, 0d, 240d, 120d),
                                new RenameConfiguration("orders", "source_orders", List.of())
                        ),
                        new ModelOutputNodeDefinition(
                                outputId,
                                "模型输出",
                                new CanvasNodeLayout(640d, 0d, 240d, 120d),
                                new ModelOutputConfiguration("source_orders", UUID.randomUUID(), null, null, List.of())
                        )
                ),
                List.of(
                        new CanvasEdgeDefinition(UUID.randomUUID().toString(), inputId, renameId),
                        new CanvasEdgeDefinition(UUID.randomUUID().toString(), renameId, outputId)
                )
        );

        CanvasGraphPlan plan = CanvasGraphPlan.create(definition);

        assertFalse(hasDegreeIssue(plan, 1));
    }

    @Test
    void appliesStreamingAppendRuleOnlyAfterWriteModeIsConfigured() {
        CanvasGraphPlan unconfigured = streamingJdbcOutputPlan(null);
        CanvasGraphPlan overwrite = streamingJdbcOutputPlan(JdbcWriteMode.OVERWRITE);

        assertFalse(hasNodeIssue(unconfigured, 1, "STREAMING_JDBC_OUTPUT_REQUIRES_APPEND"));
        assertTrue(hasNodeIssue(overwrite, 1, "STREAMING_JDBC_OUTPUT_REQUIRES_APPEND"));
    }

    private static CanvasGraphPlan streamingJdbcOutputPlan(JdbcWriteMode writeMode) {
        String inputId = UUID.randomUUID().toString();
        String outputId = UUID.randomUUID().toString();
        return CanvasGraphPlan.create(
                new CanvasDefinition(
                        1,
                        5,
                        List.of(
                                new KafkaInputNodeDefinition(
                                        inputId,
                                        "Kafka 输入",
                                        new CanvasNodeLayout(0d, 0d, 240d, 120d),
                                        new KafkaInputConfiguration(
                                                UUID.randomUUID(),
                                                "order-events",
                                                new KafkaValueSchema(List.of()),
                                                "order_events",
                                                KafkaStartingOffsets.LATEST
                                        )
                                ),
                                new JdbcOutputNodeDefinition(
                                        outputId,
                                        "JDBC 输出",
                                        new CanvasNodeLayout(320d, 0d, 240d, 120d),
                                        new JdbcOutputConfiguration(
                                                "order_events",
                                                UUID.randomUUID().toString(),
                                                "order_events",
                                                writeMode,
                                                ColumnMappingMode.BY_NAME,
                                                List.of()
                                        )
                                )
                        ),
                        List.of(new CanvasEdgeDefinition(UUID.randomUUID().toString(), inputId, outputId))
                ),
                CanvasExecutionMode.STREAMING
        );
    }

    private static boolean hasDegreeIssue(CanvasGraphPlan plan, int entryIndex) {
        return hasNodeIssue(plan, entryIndex, "INVALID_NODE_DEGREE");
    }

    private static boolean hasNodeIssue(CanvasGraphPlan plan, int entryIndex, String code) {
        return plan.entries().get(entryIndex).result().result().issues().stream()
                .anyMatch(issue -> issue.code().equals(code));
    }

    private static boolean hasCanvasIssue(CanvasGraphPlan plan, String code) {
        return plan.canvasIssues().stream().anyMatch(issue -> issue.code().equals(code));
    }
}
