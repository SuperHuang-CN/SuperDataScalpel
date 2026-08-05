package cn.superhuang.datascalpel.taskengine.compiler.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasEdgeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout;
import cn.superhuang.data.scalpel.contract.task.ColumnMappingMode;
import cn.superhuang.data.scalpel.contract.task.FileOutputConfiguration;
import cn.superhuang.data.scalpel.contract.task.FileOutputConflictPolicy;
import cn.superhuang.data.scalpel.contract.task.FileOutputFormatOptions;
import cn.superhuang.data.scalpel.contract.task.FileOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.GeoParquetCompressionCodec;
import cn.superhuang.data.scalpel.contract.task.GeoParquetCoveringMode;
import cn.superhuang.data.scalpel.contract.task.GeometryBufferConfiguration;
import cn.superhuang.data.scalpel.contract.task.GeometryBufferNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.GeometryExplodeConfiguration;
import cn.superhuang.data.scalpel.contract.task.GeometryExplodeNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.GeometryRepairConfiguration;
import cn.superhuang.data.scalpel.contract.task.GeometryRepairNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialMeasureMode;
import cn.superhuang.data.scalpel.contract.task.SpatialClipConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialClipNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialAggregateConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialAggregateNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputConfiguration;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcQueryInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.JdbcQueryInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcWriteMode;
import cn.superhuang.data.scalpel.contract.task.KafkaInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.KafkaInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.KafkaStartingOffsets;
import cn.superhuang.data.scalpel.contract.task.KafkaValueSchema;
import cn.superhuang.data.scalpel.contract.task.ModelInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.ModelInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ModelOutputConfiguration;
import cn.superhuang.data.scalpel.contract.task.ModelOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.NullHandlingConfiguration;
import cn.superhuang.data.scalpel.contract.task.NullHandlingNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.RenameConfiguration;
import cn.superhuang.data.scalpel.contract.task.RenameNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ShapefileAttributeMapping;
import cn.superhuang.data.scalpel.contract.task.ShapefilePackageMode;
import cn.superhuang.data.scalpel.contract.task.ShapefileShapeType;
import cn.superhuang.data.scalpel.contract.task.TopNConfiguration;
import cn.superhuang.data.scalpel.contract.task.TopNNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ValueMappingConfiguration;
import cn.superhuang.data.scalpel.contract.task.ValueMappingNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.WindowConfiguration;
import cn.superhuang.data.scalpel.contract.task.WindowNodeDefinition;
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
                                new ModelInputConfiguration(UUID.randomUUID().toString())
                        ),
                        new ModelOutputNodeDefinition(
                                outputId,
                                "模型输出",
                                new CanvasNodeLayout(320d, 0d, 240d, 120d),
                                new ModelOutputConfiguration(
                                        "orders", UUID.randomUUID().toString(), null, null, List.of())
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
        CanvasGraphPlan previousFileOutput = CanvasGraphPlan.create(new CanvasDefinition(1, 6, List.of(), List.of()));
        CanvasGraphPlan current = CanvasGraphPlan.create(new CanvasDefinition(
                1,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(),
                List.of()
        ));
        CanvasGraphPlan futureMinor = CanvasGraphPlan.create(new CanvasDefinition(
                1,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION + 1,
                List.of(),
                List.of()
        ));
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
        assertFalse(hasCanvasIssue(previousFileOutput, "UNSUPPORTED_SCHEMA_MINOR_VERSION"));
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
                        new ModelInputConfiguration(UUID.randomUUID().toString())
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

        FileOutputNodeDefinition shapefileOutput = new FileOutputNodeDefinition(
                UUID.randomUUID().toString(),
                "Shapefile 输出",
                new CanvasNodeLayout(0d, 0d, 240d, 120d),
                new FileOutputConfiguration(
                        "districts",
                        UUID.randomUUID().toString(),
                        "exports/districts",
                        FileOutputConflictPolicy.FAIL_IF_EXISTS,
                        new FileOutputFormatOptions.Shapefile(
                                "districts",
                                ShapefilePackageMode.ZIP,
                                "geom",
                                ShapefileShapeType.POLYGON,
                                List.of(new ShapefileAttributeMapping(
                                        "district_name", "DIST_NAME", 160))
                        )
                )
        );
        CanvasGraphPlan shapefileOnOneDotTwentyThree = CanvasGraphPlan.create(
                new CanvasDefinition(1, 23, List.of(shapefileOutput), List.of()));
        CanvasGraphPlan shapefileOnOneDotTwentyFour = CanvasGraphPlan.create(
                new CanvasDefinition(1, 24, List.of(shapefileOutput), List.of()));
        assertTrue(hasNodeIssue(
                shapefileOnOneDotTwentyThree, 0, "FORMAT_OPTION_REQUIRES_SCHEMA_VERSION"));
        assertFalse(hasNodeIssue(
                shapefileOnOneDotTwentyFour, 0, "FORMAT_OPTION_REQUIRES_SCHEMA_VERSION"));

        FileOutputNodeDefinition geoParquetOutput = new FileOutputNodeDefinition(
                UUID.randomUUID().toString(),
                "GeoParquet 输出",
                new CanvasNodeLayout(0d, 0d, 240d, 120d),
                new FileOutputConfiguration(
                        "districts",
                        UUID.randomUUID().toString(),
                        "exports/districts-geoparquet",
                        FileOutputConflictPolicy.FAIL_IF_EXISTS,
                        new FileOutputFormatOptions.GeoParquet(
                                "geom",
                                GeoParquetCompressionCodec.SNAPPY,
                                GeoParquetCoveringMode.ROW_BBOX)
                )
        );
        FileOutputNodeDefinition geoJsonOutput = new FileOutputNodeDefinition(
                UUID.randomUUID().toString(),
                "GeoJSON 输出",
                new CanvasNodeLayout(0d, 0d, 240d, 120d),
                new FileOutputConfiguration(
                        "districts",
                        UUID.randomUUID().toString(),
                        "exports/districts-geojson",
                        FileOutputConflictPolicy.FAIL_IF_EXISTS,
                        new FileOutputFormatOptions.GeoJson(
                                "districts", "geom", null, false)
                )
        );
        for (FileOutputNodeDefinition spatialOutput : List.of(geoParquetOutput, geoJsonOutput)) {
            CanvasGraphPlan outputOnOneDotTwentyFour = CanvasGraphPlan.create(
                    new CanvasDefinition(1, 24, List.of(spatialOutput), List.of()));
            CanvasGraphPlan outputOnOneDotTwentyFive = CanvasGraphPlan.create(
                    new CanvasDefinition(1, 25, List.of(spatialOutput), List.of()));
            assertTrue(hasNodeIssue(
                    outputOnOneDotTwentyFour, 0, "FORMAT_OPTION_REQUIRES_SCHEMA_VERSION"));
            assertFalse(hasNodeIssue(
                    outputOnOneDotTwentyFive, 0, "FORMAT_OPTION_REQUIRES_SCHEMA_VERSION"));
        }

        JdbcQueryInputNodeDefinition queryInput = new JdbcQueryInputNodeDefinition(
                UUID.randomUUID().toString(),
                "JDBC 查询输入",
                new CanvasNodeLayout(0d, 0d, 240d, 120d),
                new JdbcQueryInputConfiguration("", "SELECT 1", "query_result", "", List.of())
        );
        assertRequiresSchemaMinor(queryInput, 23);

        JdbcOutputNodeDefinition upsertOutput = new JdbcOutputNodeDefinition(
                UUID.randomUUID().toString(),
                "JDBC UPSERT",
                new CanvasNodeLayout(0d, 0d, 240d, 120d),
                new JdbcOutputConfiguration(
                        "orders", "", "orders", JdbcWriteMode.UPSERT,
                        ColumnMappingMode.BY_NAME, List.of(), List.of("order_id"))
        );
        CanvasGraphPlan upsertOnOneDotTwentyThree = CanvasGraphPlan.create(
                new CanvasDefinition(1, 23, List.of(upsertOutput), List.of()));
        CanvasGraphPlan upsertOnOneDotTwentyFour = CanvasGraphPlan.create(
                new CanvasDefinition(1, 24, List.of(upsertOutput), List.of()));
        assertTrue(hasNodeIssue(
                upsertOnOneDotTwentyThree, 0, "WRITE_MODE_REQUIRES_SCHEMA_VERSION"));
        assertFalse(hasNodeIssue(
                upsertOnOneDotTwentyFour, 0, "WRITE_MODE_REQUIRES_SCHEMA_VERSION"));

        assertRequiresSchemaMinor(
                new NullHandlingNodeDefinition(
                        UUID.randomUUID().toString(),
                        "空值处理",
                        new CanvasNodeLayout(0d, 0d, 240d, 120d),
                        new NullHandlingConfiguration("", "", List.of())
                ),
                13
        );
        assertRequiresSchemaMinor(
                new ValueMappingNodeDefinition(
                        UUID.randomUUID().toString(),
                        "值映射",
                        new CanvasNodeLayout(0d, 0d, 240d, 120d),
                        new ValueMappingConfiguration("", "", List.of())
                ),
                14
        );
        assertRequiresSchemaMinor(
                new WindowNodeDefinition(
                        UUID.randomUUID().toString(),
                        "窗口计算",
                        new CanvasNodeLayout(0d, 0d, 240d, 120d),
                        new WindowConfiguration("", "", List.of(), List.of(), List.of())
                ),
                15
        );
        assertRequiresSchemaMinor(
                new TopNNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Top N",
                        new CanvasNodeLayout(0d, 0d, 240d, 120d),
                        new TopNConfiguration("", "", List.of(), List.of(), 10, null)
                ),
                16
        );
        assertRequiresSchemaMinor(
                new GeometryRepairNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 修复",
                        new CanvasNodeLayout(0d, 0d, 240d, 120d),
                        new GeometryRepairConfiguration("", "", "", "")
                ),
                21
        );
        assertRequiresSchemaMinor(
                new GeometryBufferNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry Buffer",
                        new CanvasNodeLayout(0d, 0d, 240d, 120d),
                        new GeometryBufferConfiguration(
                                "", "", "", "", 100d, SpatialMeasureMode.PLANAR)
                ),
                21
        );
        assertRequiresSchemaMinor(
                new GeometryExplodeNodeDefinition(
                        UUID.randomUUID().toString(),
                        "Geometry 拆分",
                        new CanvasNodeLayout(0d, 0d, 240d, 120d),
                        new GeometryExplodeConfiguration("", "", "", "", null)
                ),
                21
        );
        assertRequiresSchemaMinor(
                new SpatialClipNodeDefinition(
                        UUID.randomUUID().toString(),
                        "空间裁剪",
                        new CanvasNodeLayout(0d, 0d, 240d, 120d),
                        new SpatialClipConfiguration("", "", "", "", "", "")
                ),
                22
        );
        assertRequiresSchemaMinor(
                new SpatialAggregateNodeDefinition(
                        UUID.randomUUID().toString(),
                        "空间聚合",
                        new CanvasNodeLayout(0d, 0d, 240d, 120d),
                        new SpatialAggregateConfiguration("", "", List.of(), List.of())
                ),
                22
        );
    }

    @Test
    void appliesSpatialClipAndSpatialAggregateDegreeRules() {
        String leftInputId = UUID.randomUUID().toString();
        String rightInputId = UUID.randomUUID().toString();
        String clipId = UUID.randomUUID().toString();
        String clipOutputId = UUID.randomUUID().toString();
        CanvasDefinition clipDefinition = new CanvasDefinition(
                1,
                23,
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
                1, 23, clipDefinition.nodes(), clipDefinition.edges().subList(0, 2)));
        assertTrue(hasDegreeIssue(invalidClip, 2));

        String aggregateInputId = UUID.randomUUID().toString();
        String aggregateId = UUID.randomUUID().toString();
        String aggregateOutputId = UUID.randomUUID().toString();
        CanvasDefinition aggregateDefinition = new CanvasDefinition(
                1,
                23,
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
                                new ModelInputConfiguration(UUID.randomUUID().toString())
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
                                new ModelOutputConfiguration(
                                        "source_orders",
                                        UUID.randomUUID().toString(),
                                        null,
                                        null,
                                        List.of())
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
    void rejectsOnlyStreamingOverwriteAfterWriteModeIsConfigured() {
        CanvasGraphPlan unconfigured = streamingJdbcOutputPlan(null);
        CanvasGraphPlan overwrite = streamingJdbcOutputPlan(JdbcWriteMode.OVERWRITE);
        CanvasGraphPlan upsert = streamingJdbcOutputPlan(JdbcWriteMode.UPSERT);

        assertFalse(hasNodeIssue(unconfigured, 1, "STREAMING_JDBC_OUTPUT_OVERWRITE_NOT_SUPPORTED"));
        assertTrue(hasNodeIssue(overwrite, 1, "STREAMING_JDBC_OUTPUT_OVERWRITE_NOT_SUPPORTED"));
        assertFalse(hasNodeIssue(upsert, 1, "STREAMING_JDBC_OUTPUT_OVERWRITE_NOT_SUPPORTED"));
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

    private static ModelInputNodeDefinition modelInput(String id, String name) {
        return new ModelInputNodeDefinition(
                id,
                name,
                new CanvasNodeLayout(0d, 0d, 240d, 120d),
                new ModelInputConfiguration(UUID.randomUUID().toString())
        );
    }

    private static ModelOutputNodeDefinition modelOutput(String id, String sourceTableName) {
        return new ModelOutputNodeDefinition(
                id,
                "输出",
                new CanvasNodeLayout(640d, 0d, 240d, 120d),
                new ModelOutputConfiguration(
                        sourceTableName,
                        UUID.randomUUID().toString(),
                        null,
                        null,
                        List.of()
                )
        );
    }

    private static boolean hasNodeIssue(CanvasGraphPlan plan, int entryIndex, String code) {
        return plan.entries().get(entryIndex).result().result().issues().stream()
                .anyMatch(issue -> issue.code().equals(code));
    }

    private static boolean hasCanvasIssue(CanvasGraphPlan plan, String code) {
        return plan.canvasIssues().stream().anyMatch(issue -> issue.code().equals(code));
    }

    private static void assertRequiresSchemaMinor(
            CanvasNodeDefinition node,
            int schemaMinorVersion
    ) {
        CanvasGraphPlan plan = CanvasGraphPlan.create(new CanvasDefinition(
                1,
                schemaMinorVersion,
                List.of(node),
                List.of()
        ));
        assertTrue(hasNodeIssue(plan, 0, "NODE_TYPE_REQUIRES_SCHEMA_VERSION"));
    }
}
