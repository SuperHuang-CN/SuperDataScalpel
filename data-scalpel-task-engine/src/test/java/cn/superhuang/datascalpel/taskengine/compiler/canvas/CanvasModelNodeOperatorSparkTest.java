package cn.superhuang.datascalpel.taskengine.compiler.canvas;

import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasEdgeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasJdbcDatabaseType;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout;
import cn.superhuang.data.scalpel.contract.task.ConnectionKind;
import cn.superhuang.data.scalpel.contract.task.DataSourcePurpose;
import cn.superhuang.data.scalpel.contract.task.JdbcWriteMode;
import cn.superhuang.data.scalpel.contract.task.JdbcColumnMapping;
import cn.superhuang.data.scalpel.contract.task.MetadataDataSource;
import cn.superhuang.data.scalpel.contract.task.MetadataModel;
import cn.superhuang.data.scalpel.contract.task.MetadataModelPhysicalTableMode;
import cn.superhuang.data.scalpel.contract.task.MetadataModelStatus;
import cn.superhuang.data.scalpel.contract.task.MetadataSnapshot;
import cn.superhuang.data.scalpel.contract.task.MetadataUniqueKey;
import cn.superhuang.data.scalpel.contract.task.MetadataUniqueKeyType;
import cn.superhuang.data.scalpel.contract.task.ModelInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.ModelInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ModelOutputConfiguration;
import cn.superhuang.data.scalpel.contract.task.ModelOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.NodeCompilationResult;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.spark.SedonaSparkSupport;
import org.apache.spark.scheduler.SparkListener;
import org.apache.spark.scheduler.SparkListenerJobStart;
import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CanvasModelNodeOperatorSparkTest {
    private final CanvasTaskCompiler compiler = new CanvasTaskCompiler();
    private final UUID dataSourceId = UUID.randomUUID();
    private final UUID inputModelId = UUID.randomUUID();
    private final UUID outputModelId = UUID.randomUUID();
    private SparkSession sparkSession;

    @BeforeAll
    void startSpark() {
        sparkSession = SedonaSparkSupport.initialize(SedonaSparkSupport.builder()
                .master("local[1]")
                .appName("canvas-model-node-compiler-test")
                .config("spark.ui.enabled", "false")
                .config("spark.driver.host", "127.0.0.1")
                .config("spark.driver.bindAddress", "127.0.0.1")
                .config("spark.sql.caseSensitive", "true")
                .config("spark.sql.ansi.enabled", "true")
                .config("spark.sql.session.timeZone", "UTC")
                .getOrCreate());
    }

    @AfterAll
    void stopSpark() {
        if (sparkSession != null) sparkSession.stop();
    }

    @Test
    void compilesPublishedModelInputAndManagedModelOutputWithoutSparkActions() throws Exception {
        AtomicInteger jobsStarted = new AtomicInteger();
        sparkSession.sparkContext().addSparkListener(new SparkListener() {
            @Override
            public void onJobStart(SparkListenerJobStart jobStart) {
                jobsStarted.incrementAndGet();
            }
        });

        CanvasCompilation compilation = compile(definition(inputModelId, outputModelId, JdbcWriteMode.APPEND),
                metadata(models()));
        sparkSession.sparkContext().listenerBus().waitUntilEmpty(10_000);

        assertTrue(compilation.valid(), () -> compilation.nodeResults().toString());
        assertEquals(0, jobsStarted.get());
        assertEquals("orders_model", compilation.nodeResults().getFirst().outputTables().getFirst().name());
        assertEquals("MODEL", compilation.nodeResults().getFirst().outputTables().getFirst().origin().kind());
        assertEquals(inputModelId, compilation.nodeResults().getFirst().outputTables().getFirst().origin().modelId());
    }

    @Test
    void rejectsUnpublishedInputAndExternalOverwrite() {
        List<MetadataModel> unpublishedModels = List.of(
                model(inputModelId, "orders_model", MetadataModelStatus.DISABLED,
                        MetadataModelPhysicalTableMode.MANAGED),
                model(outputModelId, "orders_target", MetadataModelStatus.PUBLISHED,
                        MetadataModelPhysicalTableMode.MANAGED)
        );
        CanvasCompilation unpublished = compile(
                definition(inputModelId, outputModelId, JdbcWriteMode.APPEND),
                metadata(unpublishedModels)
        );
        assertFalse(unpublished.valid());
        assertIssue(unpublished.nodeResults().get(0), "MODEL_NOT_PUBLISHED");

        List<MetadataModel> externalModels = List.of(
                model(inputModelId, "orders_model", MetadataModelStatus.PUBLISHED,
                        MetadataModelPhysicalTableMode.MANAGED),
                model(outputModelId, "orders_target", MetadataModelStatus.PUBLISHED,
                        MetadataModelPhysicalTableMode.EXTERNAL)
        );
        CanvasCompilation externalOverwrite = compile(
                definition(inputModelId, outputModelId, JdbcWriteMode.OVERWRITE),
                metadata(externalModels)
        );
        assertFalse(externalOverwrite.valid());
        assertIssue(externalOverwrite.nodeResults().get(1), "OVERWRITE_REQUIRES_MANAGED_MODEL");
    }

    @Test
    void compilesModelOutputUpsertWithModelPrimaryKeyAndRejectsMissingKeyMapping() {
        CanvasCompilation valid = compile(
                definition(inputModelId, outputModelId, JdbcWriteMode.UPSERT),
                metadata(models())
        );
        assertTrue(valid.valid(), () -> valid.nodeResults().toString());

        CanvasDefinition base = definition(inputModelId, outputModelId, JdbcWriteMode.UPSERT);
        ModelOutputNodeDefinition output = (ModelOutputNodeDefinition) base.nodes().getLast();
        ModelOutputNodeDefinition missingKey = new ModelOutputNodeDefinition(
                output.id(), output.name(), output.layout(),
                new ModelOutputConfiguration(
                        output.configuration().sourceTableName(),
                        output.configuration().targetModelId(),
                        JdbcWriteMode.UPSERT,
                        List.of(new JdbcColumnMapping("description", "description"))
                )
        );
        CanvasCompilation invalid = compile(
                new CanvasDefinition(
                        CanvasDefinition.CURRENT_SCHEMA_VERSION,
                        CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                        List.of(base.nodes().getFirst(), missingKey),
                        base.edges()),
                metadata(models())
        );

        assertFalse(invalid.valid());
        assertIssue(invalid.nodeResults().getLast(), "UPSERT_KEY_NOT_MAPPED");
    }

    @Test
    void rejectsModelOutputUpsertWhenModelHasNoPrimaryKey() {
        List<MetadataModel> withoutPrimaryKey = List.of(
                model(inputModelId, "orders_model", MetadataModelStatus.PUBLISHED,
                        MetadataModelPhysicalTableMode.MANAGED),
                model(outputModelId, "orders_target", MetadataModelStatus.PUBLISHED,
                        MetadataModelPhysicalTableMode.MANAGED, false, false)
        );
        CanvasCompilation compilation = compile(
                definition(inputModelId, outputModelId, JdbcWriteMode.UPSERT),
                metadata(withoutPrimaryKey)
        );

        assertFalse(compilation.valid());
        assertIssue(compilation.nodeResults().getLast(), "UPSERT_KEY_REQUIRED");
    }

    @Test
    void compilesGeometryModelsWithSedonaSchema() {
        List<MetadataModel> geometryModels = List.of(
                model(inputModelId, "orders_model", MetadataModelStatus.PUBLISHED,
                        MetadataModelPhysicalTableMode.MANAGED, true),
                model(outputModelId, "orders_target", MetadataModelStatus.PUBLISHED,
                        MetadataModelPhysicalTableMode.MANAGED, true)
        );
        CanvasCompilation compilation = compile(
                definition(inputModelId, outputModelId, JdbcWriteMode.APPEND),
                metadata(geometryModels)
        );

        assertTrue(compilation.valid(), () -> compilation.nodeResults().toString());
        CanvasColumnSchema geometry = compilation.nodeResults().getFirst().outputTables().getFirst()
                .columns().stream()
                .filter(column -> column.fieldType() == PlatformDataType.GEOMETRY)
                .findFirst()
                .orElseThrow();
        assertEquals(
                new GeometryTypeDefinition(
                        GeometryKind.POINT,
                        new CrsReference("EPSG", 4326),
                        CoordinateDimension.XY
                ),
                geometry.geometry()
        );
    }

    @Test
    void allowsDuplicateOutputTargetsAndModelReadWriteReferences() {
        CanvasDefinition base = definition(inputModelId, outputModelId, JdbcWriteMode.APPEND);
        ModelOutputNodeDefinition firstOutput = (ModelOutputNodeDefinition) base.nodes().get(1);
        ModelOutputNodeDefinition secondOutput = new ModelOutputNodeDefinition(
                UUID.randomUUID().toString(),
                "第二个模型输出",
                new CanvasNodeLayout(640d, 180d, 240d, 120d),
                firstOutput.configuration()
        );
        List<cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition> duplicateNodes =
                new ArrayList<>(base.nodes());
        duplicateNodes.add(secondOutput);
        List<CanvasEdgeDefinition> duplicateEdges = new ArrayList<>(base.edges());
        duplicateEdges.add(new CanvasEdgeDefinition(
                UUID.randomUUID().toString(),
                base.nodes().getFirst().id(),
                secondOutput.id()
        ));
        CanvasCompilation duplicate = compile(
                new CanvasDefinition(2, 0, duplicateNodes, duplicateEdges),
                metadata(models())
        );
        assertTrue(duplicate.valid());

        CanvasCompilation conflict = compile(
                definition(inputModelId, inputModelId, JdbcWriteMode.APPEND),
                metadata(models())
        );
        assertTrue(conflict.valid());
    }

    private CanvasCompilation compile(CanvasDefinition definition, MetadataSnapshot metadata) {
        return compiler.compile(
                definition,
                MetadataIndex.create(metadata),
                sparkSession.newSession(),
                new AtomicBoolean()
        );
    }

    private CanvasDefinition definition(UUID sourceModelId, UUID targetModelId, JdbcWriteMode writeMode) {
        String inputNodeId = UUID.randomUUID().toString();
        String outputNodeId = UUID.randomUUID().toString();
        return new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(
                        new ModelInputNodeDefinition(
                                inputNodeId,
                                "模型输入",
                                new CanvasNodeLayout(0d, 0d, 240d, 120d),
                                new ModelInputConfiguration(sourceModelId.toString())
                        ),
                        new ModelOutputNodeDefinition(
                                outputNodeId,
                                "模型输出",
                                new CanvasNodeLayout(320d, 0d, 240d, 120d),
                                new ModelOutputConfiguration(
                                        "orders_model",
                                        targetModelId.toString(),
                                        writeMode,
                                        List.of(
                                                new JdbcColumnMapping("id", "id"),
                                                new JdbcColumnMapping("description", "description")
                                        )
                                )
                        )
                ),
                List.of(new CanvasEdgeDefinition(UUID.randomUUID().toString(), inputNodeId, outputNodeId))
        );
    }

    private MetadataSnapshot metadata(List<MetadataModel> models) {
        return new MetadataSnapshot(
                List.of(new MetadataDataSource(
                        dataSourceId,
                        true,
                        ConnectionKind.JDBC,
                        CanvasJdbcDatabaseType.POSTGRESQL,
                        Set.of(DataSourcePurpose.SOURCE, DataSourcePurpose.STORAGE),
                        List.of()
                )),
                models
        );
    }

    private List<MetadataModel> models() {
        return List.of(
                model(inputModelId, "orders_model", MetadataModelStatus.PUBLISHED,
                        MetadataModelPhysicalTableMode.MANAGED),
                model(outputModelId, "orders_target", MetadataModelStatus.PUBLISHED,
                        MetadataModelPhysicalTableMode.MANAGED)
        );
    }

    private MetadataModel model(
            UUID id,
            String code,
            MetadataModelStatus status,
            MetadataModelPhysicalTableMode physicalTableMode
    ) {
        return model(id, code, status, physicalTableMode, false, true);
    }

    private MetadataModel model(
            UUID id,
            String code,
            MetadataModelStatus status,
            MetadataModelPhysicalTableMode physicalTableMode,
            boolean includeGeometry
    ) {
        return model(id, code, status, physicalTableMode, includeGeometry, true);
    }

    private MetadataModel model(
            UUID id,
            String code,
            MetadataModelStatus status,
            MetadataModelPhysicalTableMode physicalTableMode,
            boolean includeGeometry,
            boolean includePrimaryKey
    ) {
        List<CanvasColumnSchema> columns = new ArrayList<>(
                List.of(column("id", false), column("description", true))
        );
        if (includeGeometry) {
            columns.add(new CanvasColumnSchema(
                    "shape",
                    PlatformDataType.GEOMETRY,
                    null,
                    null,
                    null,
                    true,
                    null,
                    false,
                    false,
                    null,
                    new GeometryTypeDefinition(
                            GeometryKind.POINT,
                            new CrsReference("EPSG", 4326),
                            CoordinateDimension.XY
                    )
            ));
        }
        return new MetadataModel(
                id,
                code,
                code,
                3,
                status,
                physicalTableMode,
                dataSourceId,
                "warehouse",
                "public",
                "dwd_" + code,
                columns,
                includePrimaryKey
                        ? List.of(new MetadataUniqueKey(
                        "MODEL_PRIMARY_KEY", MetadataUniqueKeyType.PRIMARY_KEY, List.of("id")))
                        : List.of()
        );
    }

    private static CanvasColumnSchema column(String name, boolean nullable) {
        return new CanvasColumnSchema(
                name,
                name.equals("description") ? PlatformDataType.STRING : PlatformDataType.LONG,
                name.equals("description") ? 200 : null,
                null,
                null,
                nullable,
                null,
                false,
                false,
                null
        );
    }

    private static void assertIssue(NodeCompilationResult result, String code) {
        assertTrue(result.issues().stream().anyMatch(issue -> issue.code().equals(code)),
                () -> "Expected issue " + code + " but got " + result.issues());
    }
}
