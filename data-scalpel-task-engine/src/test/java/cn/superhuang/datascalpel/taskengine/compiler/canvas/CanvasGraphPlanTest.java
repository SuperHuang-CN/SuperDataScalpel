package cn.superhuang.datascalpel.taskengine.compiler.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasEdgeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout;
import cn.superhuang.data.scalpel.contract.task.CompilationSeverity;
import cn.superhuang.data.scalpel.contract.task.CastFailureStrategy;
import cn.superhuang.data.scalpel.contract.task.ColumnTypeCast;
import cn.superhuang.data.scalpel.contract.task.EpochTimestampUnit;
import cn.superhuang.data.scalpel.contract.task.ProcessorOutput;
import cn.superhuang.data.scalpel.contract.task.SpatialClipConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialClipNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialAggregateConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialAggregateNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputConfiguration;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputWrite;
import cn.superhuang.data.scalpel.contract.task.JdbcColumnMapping;
import cn.superhuang.data.scalpel.contract.task.JdbcWriteMode;
import cn.superhuang.data.scalpel.contract.task.JoinCondition;
import cn.superhuang.data.scalpel.contract.task.JoinConfiguration;
import cn.superhuang.data.scalpel.contract.task.JoinNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JoinOperator;
import cn.superhuang.data.scalpel.contract.task.JoinType;
import cn.superhuang.data.scalpel.contract.task.KafkaInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.KafkaInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.KafkaStartingOffsets;
import cn.superhuang.data.scalpel.contract.task.KafkaValueSchema;
import cn.superhuang.data.scalpel.contract.task.ModelInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.ModelInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ModelInputSelection;
import cn.superhuang.data.scalpel.contract.task.ModelOutputConfiguration;
import cn.superhuang.data.scalpel.contract.task.ModelOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ModelOutputWrite;
import cn.superhuang.data.scalpel.contract.task.RenameConfiguration;
import cn.superhuang.data.scalpel.contract.task.RenameNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.TypeCastConfiguration;
import cn.superhuang.data.scalpel.contract.task.TypeCastNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.TypeCastOperation;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanvasGraphPlanTest {

    @Test
    void allowsJoinToReceiveOneOrMoreTableMapInputs() {
        assertFalse(hasDegreeIssue(joinPlan(1), 3));
        assertFalse(hasDegreeIssue(joinPlan(2), 3));
        assertFalse(hasDegreeIssue(joinPlan(3), 3));
        assertTrue(hasDegreeIssue(joinPlan(0), 3));
    }

    @Test
    void appliesInputAndOutputDegreeRulesToModelNodes() {
        String inputId = UUID.randomUUID().toString();
        String outputId = UUID.randomUUID().toString();
        CanvasDefinition validTopology = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(
                        new ModelInputNodeDefinition(
                                inputId,
                                "模型输入",
                                new CanvasNodeLayout(0d, 0d, 240d, 120d),
                                modelInputConfiguration()
                        ),
                        new ModelOutputNodeDefinition(
                                outputId,
                                "模型输出",
                                new CanvasNodeLayout(320d, 0d, 240d, 120d),
                                modelOutputConfiguration("orders", null, List.of())
                        )
                ),
                List.of(new CanvasEdgeDefinition(UUID.randomUUID().toString(), inputId, outputId))
        );

        CanvasGraphPlan validPlan = CanvasGraphPlan.create(validTopology);
        assertFalse(hasDegreeIssue(validPlan, 0));
        assertFalse(hasDegreeIssue(validPlan, 1));

        CanvasGraphPlan invalidPlan = CanvasGraphPlan.create(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                validTopology.nodes(),
                List.of(new CanvasEdgeDefinition(UUID.randomUUID().toString(), outputId, inputId))
        ));
        assertTrue(hasDegreeIssue(invalidPlan, 0));
        assertTrue(hasDegreeIssue(invalidPlan, 1));
    }

    @Test
    void acceptsOnlyCurrentCanvasMajorAndMinorVersion() {
        CanvasGraphPlan current = CanvasGraphPlan.create(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(),
                List.of()
        ));
        CanvasGraphPlan previousMajor = CanvasGraphPlan.create(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION - 1,
                3,
                List.of(),
                List.of()
        ));
        CanvasGraphPlan legacyMajor = CanvasGraphPlan.create(new CanvasDefinition(
                1,
                28,
                List.of(),
                List.of()
        ));
        CanvasGraphPlan futureMinor = CanvasGraphPlan.create(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION + 1,
                List.of(),
                List.of()
        ));
        CanvasGraphPlan futureMajor = CanvasGraphPlan.create(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION + 1,
                0,
                List.of(),
                List.of()
        ));

        assertFalse(hasCanvasIssue(current, "UNSUPPORTED_SCHEMA_VERSION"));
        assertFalse(hasCanvasIssue(current, "UNSUPPORTED_SCHEMA_MINOR_VERSION"));
        assertTrue(hasCanvasIssue(previousMajor, "UNSUPPORTED_SCHEMA_VERSION"));
        assertTrue(hasCanvasIssue(legacyMajor, "UNSUPPORTED_SCHEMA_VERSION"));
        assertTrue(hasCanvasIssue(futureMinor, "UNSUPPORTED_SCHEMA_MINOR_VERSION"));
        assertTrue(hasCanvasIssue(futureMajor, "UNSUPPORTED_SCHEMA_VERSION"));
    }

    @Test
    void rejectsEpochTimestampUnitsInDefinitionsBeforeCanvasFourDotTwo() {
        TypeCastNodeDefinition typeCast = new TypeCastNodeDefinition(
                UUID.randomUUID().toString(),
                "毫秒时间戳转换",
                new CanvasNodeLayout(0d, 0d, 240d, 120d),
                new TypeCastConfiguration(List.of(new TypeCastOperation(
                        UUID.randomUUID().toString(),
                        "events",
                        new ProcessorOutput.CreateNewTable("typed_events"),
                        List.of(new ColumnTypeCast(
                                "event_time_ms",
                                new PlatformTypeDefinition(PlatformDataType.TIMESTAMP, null, null, null, null),
                                CastFailureStrategy.FAIL,
                                EpochTimestampUnit.MILLISECONDS
                        ))
                )))
        );

        CanvasGraphPlan fourDotOne = CanvasGraphPlan.create(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                1,
                List.of(typeCast),
                List.of()
        ));
        CanvasGraphPlan current = CanvasGraphPlan.create(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(typeCast),
                List.of()
        ));

        assertTrue(hasNodeIssueAtPath(
                fourDotOne,
                0,
                "EPOCH_TIMESTAMP_UNIT_SCHEMA_MINOR_VERSION_NOT_SUPPORTED",
                "configuration.operations[0].casts[0].epochTimestampUnit"
        ));
        assertFalse(hasNodeIssue(current, 0, "EPOCH_TIMESTAMP_UNIT_SCHEMA_MINOR_VERSION_NOT_SUPPORTED"));
    }

    @Test
    void rejectsStringTemporalParsingInDefinitionsBeforeCanvasFourDotThree() {
        TypeCastNodeDefinition typeCast = new TypeCastNodeDefinition(
                UUID.randomUUID().toString(),
                "字符串时间转换",
                new CanvasNodeLayout(0d, 0d, 240d, 120d),
                new TypeCastConfiguration(List.of(new TypeCastOperation(
                        UUID.randomUUID().toString(),
                        "events",
                        new ProcessorOutput.CreateNewTable("typed_events"),
                        List.of(new ColumnTypeCast(
                                "created_at_text",
                                new PlatformTypeDefinition(PlatformDataType.TIMESTAMP, null, null, null, null),
                                CastFailureStrategy.FAIL,
                                null,
                                new cn.superhuang.data.scalpel.contract.task.StringTemporalParseOptions(
                                        "yyyy-MM-dd HH:mm:ss",
                                        cn.superhuang.data.scalpel.contract.task.StringTimestampZoneMode.SOURCE_TIME_ZONE,
                                        "Asia/Shanghai"
                                )
                        ))
                )))
        );

        CanvasGraphPlan fourDotTwo = CanvasGraphPlan.create(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                2,
                List.of(typeCast),
                List.of()
        ));
        CanvasGraphPlan current = CanvasGraphPlan.create(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(typeCast),
                List.of()
        ));

        assertTrue(hasNodeIssueAtPath(
                fourDotTwo,
                0,
                "STRING_TEMPORAL_PARSE_SCHEMA_MINOR_VERSION_NOT_SUPPORTED",
                "configuration.operations[0].casts[0].stringTemporalParseOptions"
        ));
        assertFalse(hasNodeIssue(current, 0, "STRING_TEMPORAL_PARSE_SCHEMA_MINOR_VERSION_NOT_SUPPORTED"));
    }

    @Test
    void rejectsTemporalStringFormattingInDefinitionsBeforeCanvasFourDotSeven() {
        TypeCastNodeDefinition typeCast = new TypeCastNodeDefinition(
                UUID.randomUUID().toString(),
                "时间字符串格式化",
                new CanvasNodeLayout(0d, 0d, 240d, 120d),
                new TypeCastConfiguration(List.of(new TypeCastOperation(
                        UUID.randomUUID().toString(),
                        "events",
                        new ProcessorOutput.CreateNewTable("typed_events"),
                        List.of(new ColumnTypeCast(
                                "created_at",
                                new PlatformTypeDefinition(PlatformDataType.STRING, null, null, null, null),
                                CastFailureStrategy.FAIL,
                                null,
                                null,
                                new cn.superhuang.data.scalpel.contract.task.TemporalStringFormatOptions(
                                        "yyyy-MM-dd HH:mm:ss", "UTC"
                                )
                        ))
                )))
        );

        CanvasGraphPlan fourDotSix = CanvasGraphPlan.create(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                6,
                List.of(typeCast),
                List.of()
        ));
        CanvasGraphPlan current = CanvasGraphPlan.create(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(typeCast),
                List.of()
        ));

        assertTrue(hasNodeIssueAtPath(
                fourDotSix,
                0,
                "TEMPORAL_STRING_FORMAT_SCHEMA_MINOR_VERSION_NOT_SUPPORTED",
                "configuration.operations[0].casts[0].temporalStringFormatOptions"
        ));
        assertFalse(hasNodeIssue(
                current,
                0,
                "TEMPORAL_STRING_FORMAT_SCHEMA_MINOR_VERSION_NOT_SUPPORTED"
        ));
    }

    @Test
    void rejectsTemporalToEpochLongInDefinitionsBeforeCanvasFourDotEight() {
        TypeCastNodeDefinition typeCast = new TypeCastNodeDefinition(
                UUID.randomUUID().toString(),
                "时间转 Epoch",
                new CanvasNodeLayout(0d, 0d, 240d, 120d),
                new TypeCastConfiguration(List.of(new TypeCastOperation(
                        UUID.randomUUID().toString(),
                        "events",
                        new ProcessorOutput.CreateNewTable("typed_events"),
                        List.of(new ColumnTypeCast(
                                "created_at",
                                new PlatformTypeDefinition(PlatformDataType.LONG, null, null, null, null),
                                CastFailureStrategy.FAIL,
                                EpochTimestampUnit.MILLISECONDS
                        ))
                )))
        );

        CanvasGraphPlan fourDotSeven = CanvasGraphPlan.create(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                7,
                List.of(typeCast),
                List.of()
        ));
        CanvasGraphPlan current = CanvasGraphPlan.create(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(typeCast),
                List.of()
        ));

        assertTrue(hasNodeIssueAtPath(
                fourDotSeven,
                0,
                "TEMPORAL_TO_EPOCH_SCHEMA_MINOR_VERSION_NOT_SUPPORTED",
                "configuration.operations[0].casts[0].epochTimestampUnit"
        ));
        assertFalse(hasNodeIssue(
                current,
                0,
                "TEMPORAL_TO_EPOCH_SCHEMA_MINOR_VERSION_NOT_SUPPORTED"
        ));
    }

    @Test
    void allowsTerminalSpatialProcessorsWithAnUnusedOutputWarning() {
        String leftInputId = UUID.randomUUID().toString();
        String rightInputId = UUID.randomUUID().toString();
        String clipId = UUID.randomUUID().toString();
        String clipOutputId = UUID.randomUUID().toString();
        CanvasDefinition clipDefinition = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(
                        modelInput(leftInputId, "来源输入"),
                        modelInput(rightInputId, "Mask 输入"),
                        new SpatialClipNodeDefinition(
                                clipId,
                                "空间裁剪",
                                new CanvasNodeLayout(320d, 0d, 240d, 120d),
                                new SpatialClipConfiguration(
                                        "roads", "districts", "clipped",
                                        "shape", "boundary", "clipped_shape")
                        ),
                        modelOutput(clipOutputId, "clipped")
                ),
                List.of(
                        new CanvasEdgeDefinition(UUID.randomUUID().toString(), leftInputId, clipId),
                        new CanvasEdgeDefinition(UUID.randomUUID().toString(), rightInputId, clipId),
                        new CanvasEdgeDefinition(UUID.randomUUID().toString(), clipId, clipOutputId)
                )
        );
        CanvasGraphPlan validClip = CanvasGraphPlan.create(clipDefinition);
        assertFalse(hasDegreeIssue(validClip, 2));
        CanvasGraphPlan invalidClip = CanvasGraphPlan.create(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                clipDefinition.nodes(),
                clipDefinition.edges().subList(0, 2)));
        assertFalse(hasDegreeIssue(invalidClip, 2));
        assertTrue(hasNodeWarning(invalidClip, 2, "UNCONSUMED_PROCESSOR_OUTPUT"));

        String aggregateInputId = UUID.randomUUID().toString();
        String aggregateId = UUID.randomUUID().toString();
        String aggregateOutputId = UUID.randomUUID().toString();
        CanvasDefinition aggregateDefinition = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(
                        modelInput(aggregateInputId, "来源输入"),
                        new SpatialAggregateNodeDefinition(
                                aggregateId,
                                "空间聚合",
                                new CanvasNodeLayout(320d, 0d, 240d, 120d),
                                new SpatialAggregateConfiguration(
                                        "parcels", "districts", List.of(), List.of())
                        ),
                        modelOutput(aggregateOutputId, "districts")
                ),
                List.of(
                        new CanvasEdgeDefinition(
                                UUID.randomUUID().toString(), aggregateInputId, aggregateId),
                        new CanvasEdgeDefinition(
                                UUID.randomUUID().toString(), aggregateId, aggregateOutputId)
                )
        );
        CanvasGraphPlan validAggregate = CanvasGraphPlan.create(aggregateDefinition);
        assertFalse(hasDegreeIssue(validAggregate, 1));
    }

    @Test
    void allowsRenameToMergeMultipleInputTableMaps() {
        String inputId = UUID.randomUUID().toString();
        String secondInputId = UUID.randomUUID().toString();
        String renameId = UUID.randomUUID().toString();
        String outputId = UUID.randomUUID().toString();
        CanvasDefinition definition = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(
                        new ModelInputNodeDefinition(
                                inputId,
                                "模型输入",
                                new CanvasNodeLayout(0d, 0d, 240d, 120d),
                                modelInputConfiguration()
                        ),
                        new ModelInputNodeDefinition(
                                secondInputId,
                                "第二个模型输入",
                                new CanvasNodeLayout(0d, 180d, 240d, 120d),
                                modelInputConfiguration()
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
                                modelOutputConfiguration("source_orders", null, List.of())
                        )
                ),
                List.of(
                        new CanvasEdgeDefinition(UUID.randomUUID().toString(), inputId, renameId),
                        new CanvasEdgeDefinition(UUID.randomUUID().toString(), secondInputId, renameId),
                        new CanvasEdgeDefinition(UUID.randomUUID().toString(), renameId, outputId)
                )
        );

        CanvasGraphPlan plan = CanvasGraphPlan.create(definition);

        assertFalse(hasDegreeIssue(plan, 2));
    }

    @Test
    void rejectsOnlyStreamingOverwriteAfterWriteModeIsConfigured() {
        CanvasGraphPlan unconfigured = streamingJdbcOutputPlan(null);
        CanvasGraphPlan overwrite = streamingJdbcOutputPlan(JdbcWriteMode.OVERWRITE);
        CanvasGraphPlan upsert = streamingJdbcOutputPlan(JdbcWriteMode.UPSERT);

        assertFalse(hasNodeIssue(unconfigured, 1, "STREAMING_JDBC_OUTPUT_OVERWRITE_NOT_SUPPORTED"));
        assertTrue(hasNodeIssue(overwrite, 1, "STREAMING_JDBC_OUTPUT_OVERWRITE_NOT_SUPPORTED"));
        assertFalse(hasNodeIssue(upsert, 1, "STREAMING_JDBC_OUTPUT_OVERWRITE_NOT_SUPPORTED"));
    }

    @Test
    void rejectsOverwriteInTheSecondStreamingOutputWriteWithIndexedPath() {
        CanvasGraphPlan jdbc = streamingJdbcOutputPlanWithModes(
                JdbcWriteMode.APPEND, JdbcWriteMode.OVERWRITE);
        CanvasGraphPlan model = streamingModelOutputPlanWithModes(
                JdbcWriteMode.APPEND, JdbcWriteMode.OVERWRITE);

        assertTrue(hasNodeIssueAtPath(
                jdbc, 1, "STREAMING_JDBC_OUTPUT_OVERWRITE_NOT_SUPPORTED",
                "configuration.writes[1].writeMode"));
        assertTrue(hasNodeIssueAtPath(
                model, 1, "STREAMING_MODEL_OUTPUT_OVERWRITE_NOT_SUPPORTED",
                "configuration.writes[1].writeMode"));
    }

    @Test
    void treatsModelOutputUpsertAsAStreamingOutputInCurrentCanvasVersion() {
        String inputId = UUID.randomUUID().toString();
        String outputId = UUID.randomUUID().toString();
        ModelOutputNodeDefinition output = new ModelOutputNodeDefinition(
                outputId,
                "模型输出",
                new CanvasNodeLayout(320d, 0d, 240d, 120d),
                modelOutputConfiguration(
                        "order_events", JdbcWriteMode.UPSERT,
                        List.of(new JdbcColumnMapping("id", "id")))
        );
        CanvasDefinition current = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(
                        new KafkaInputNodeDefinition(
                                inputId,
                                "Kafka 输入",
                                new CanvasNodeLayout(0d, 0d, 240d, 120d),
                                new KafkaInputConfiguration(
                                        UUID.randomUUID().toString(),
                                        "order-events",
                                        new KafkaValueSchema(List.of()),
                                        "order_events",
                                        KafkaStartingOffsets.LATEST
                                )
                        ),
                        output
                ),
                List.of(new CanvasEdgeDefinition(UUID.randomUUID().toString(), inputId, outputId))
        );
        CanvasGraphPlan streaming = CanvasGraphPlan.create(current, CanvasExecutionMode.STREAMING);

        assertFalse(hasNodeIssue(streaming, 1, "NODE_EXECUTION_MODE_NOT_SUPPORTED"));
        assertFalse(hasCanvasIssue(streaming, "STREAMING_OUTPUT_REQUIRED"));
    }

    @Test
    void rejectsModelOverwriteButAllowsModelUpsertInStreamingMode() {
        CanvasGraphPlan overwrite = streamingModelOutputPlan(JdbcWriteMode.OVERWRITE);
        CanvasGraphPlan upsert = streamingModelOutputPlan(JdbcWriteMode.UPSERT);

        assertTrue(hasNodeIssue(overwrite, 1, "STREAMING_MODEL_OUTPUT_OVERWRITE_NOT_SUPPORTED"));
        assertFalse(hasNodeIssue(upsert, 1, "STREAMING_MODEL_OUTPUT_OVERWRITE_NOT_SUPPORTED"));
    }

    private static CanvasGraphPlan streamingJdbcOutputPlan(JdbcWriteMode writeMode) {
        return streamingJdbcOutputPlanWithModes(writeMode);
    }

    private static CanvasGraphPlan joinPlan(int incomingCount) {
        String firstInputId = UUID.randomUUID().toString();
        String secondInputId = UUID.randomUUID().toString();
        String thirdInputId = UUID.randomUUID().toString();
        String joinId = UUID.randomUUID().toString();
        String outputId = UUID.randomUUID().toString();
        List<String> inputIds = List.of(firstInputId, secondInputId, thirdInputId);
        List<CanvasEdgeDefinition> edges = new java.util.ArrayList<>();
        for (int index = 0; index < incomingCount; index++) {
            edges.add(new CanvasEdgeDefinition(
                    UUID.randomUUID().toString(), inputIds.get(index), joinId));
        }
        edges.add(new CanvasEdgeDefinition(UUID.randomUUID().toString(), joinId, outputId));
        return CanvasGraphPlan.create(new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(
                        modelInput(firstInputId, "输入一"),
                        modelInput(secondInputId, "输入二"),
                        modelInput(thirdInputId, "输入三"),
                        new JoinNodeDefinition(
                                joinId,
                                "订单客户 Join",
                                new CanvasNodeLayout(320d, 0d, 360d, 216d),
                                new JoinConfiguration(
                                        "orders",
                                        "customers",
                                        "order_customers",
                                        JoinType.INNER,
                                        List.of(new JoinCondition(
                                                "customer_id", JoinOperator.EQUALS, "id")))),
                        modelOutput(outputId, "order_customers")
                ),
                edges
        ));
    }

    private static CanvasGraphPlan streamingJdbcOutputPlanWithModes(JdbcWriteMode... writeModes) {
        String inputId = UUID.randomUUID().toString();
        String outputId = UUID.randomUUID().toString();
        return CanvasGraphPlan.create(
                new CanvasDefinition(
                        CanvasDefinition.CURRENT_SCHEMA_VERSION,
                        CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                        List.of(
                                new KafkaInputNodeDefinition(
                                        inputId,
                                        "Kafka 输入",
                                        new CanvasNodeLayout(0d, 0d, 240d, 120d),
                                        new KafkaInputConfiguration(
                                                UUID.randomUUID().toString(),
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
                                                UUID.randomUUID().toString(),
                                                java.util.stream.IntStream.range(0, writeModes.length)
                                                        .mapToObj(index -> new JdbcOutputWrite(
                                                                UUID.randomUUID().toString(),
                                                                "order_events", "order_events_" + index,
                                                                writeModes[index], List.of(), List.of()))
                                                        .toList())
                                )
                        ),
                        List.of(new CanvasEdgeDefinition(UUID.randomUUID().toString(), inputId, outputId))
                ),
                CanvasExecutionMode.STREAMING
        );
    }

    private static CanvasGraphPlan streamingModelOutputPlan(JdbcWriteMode writeMode) {
        return streamingModelOutputPlanWithModes(writeMode);
    }

    private static CanvasGraphPlan streamingModelOutputPlanWithModes(JdbcWriteMode... writeModes) {
        String inputId = UUID.randomUUID().toString();
        String outputId = UUID.randomUUID().toString();
        return CanvasGraphPlan.create(
                new CanvasDefinition(
                        CanvasDefinition.CURRENT_SCHEMA_VERSION,
                        CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                        List.of(
                                new KafkaInputNodeDefinition(
                                        inputId,
                                        "Kafka 输入",
                                        new CanvasNodeLayout(0d, 0d, 240d, 120d),
                                        new KafkaInputConfiguration(
                                                UUID.randomUUID().toString(),
                                                "order-events",
                                                new KafkaValueSchema(List.of()),
                                                "order_events",
                                                KafkaStartingOffsets.LATEST
                                        )
                                ),
                                new ModelOutputNodeDefinition(
                                        outputId,
                                        "模型输出",
                                        new CanvasNodeLayout(320d, 0d, 240d, 120d),
                                        new ModelOutputConfiguration(
                                                java.util.Arrays.stream(writeModes)
                                                        .map(writeMode -> new ModelOutputWrite(
                                                                UUID.randomUUID().toString(),
                                                                "order_events", UUID.randomUUID().toString(),
                                                                writeMode, List.of()))
                                                        .toList())
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

    private static ModelInputNodeDefinition modelInput(String id, String name) {
        return new ModelInputNodeDefinition(
                id,
                name,
                new CanvasNodeLayout(0d, 0d, 240d, 120d),
                modelInputConfiguration()
        );
    }

    private static ModelOutputNodeDefinition modelOutput(String id, String sourceTableName) {
        return new ModelOutputNodeDefinition(
                id,
                "输出",
                new CanvasNodeLayout(640d, 0d, 240d, 120d),
                modelOutputConfiguration(sourceTableName, null, List.of())
        );
    }

    private static ModelInputConfiguration modelInputConfiguration() {
        return new ModelInputConfiguration(List.of(
                new ModelInputSelection(UUID.randomUUID().toString())));
    }

    private static ModelOutputConfiguration modelOutputConfiguration(
            String sourceTableName,
            JdbcWriteMode writeMode,
            List<JdbcColumnMapping> mappings
    ) {
        return new ModelOutputConfiguration(List.of(new ModelOutputWrite(
                UUID.randomUUID().toString(), sourceTableName,
                UUID.randomUUID().toString(), writeMode, mappings)));
    }

    private static boolean hasNodeIssue(CanvasGraphPlan plan, int entryIndex, String code) {
        return plan.entries().get(entryIndex).result().result().issues().stream()
                .anyMatch(issue -> issue.code().equals(code));
    }

    private static boolean hasNodeWarning(CanvasGraphPlan plan, int entryIndex, String code) {
        return plan.entries().get(entryIndex).result().result().issues().stream()
                .anyMatch(issue -> issue.code().equals(code)
                        && issue.severity() == CompilationSeverity.WARNING);
    }

    private static boolean hasNodeIssueAtPath(
            CanvasGraphPlan plan,
            int entryIndex,
            String code,
            String path
    ) {
        return plan.entries().get(entryIndex).result().result().issues().stream()
                .anyMatch(issue -> issue.code().equals(code) && issue.path().equals(path));
    }

    private static boolean hasCanvasIssue(CanvasGraphPlan plan, String code) {
        return plan.canvasIssues().stream().anyMatch(issue -> issue.code().equals(code));
    }

}
