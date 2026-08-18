package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnection;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.datasource.service.DataSourceRuntimeService;
import cn.superhuang.data.scalpel.business.datasource.service.ApiResourceService;
import cn.superhuang.data.scalpel.business.datasource.service.SpatialFeatureResourceService;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetFieldRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetFileRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetTableRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetTableSourceRepository;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDataset;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetCompression;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetField;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFile;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFormat;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetTable;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetTableSource;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetType;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.model.domain.PhysicalTableMode;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.contract.task.*;
import cn.superhuang.data.scalpel.contract.task.CanvasEdgeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout;
import cn.superhuang.data.scalpel.contract.task.CompilationIssue;
import cn.superhuang.data.scalpel.contract.task.CompilationSeverity;
import cn.superhuang.data.scalpel.contract.task.ConnectionKind;
import cn.superhuang.data.scalpel.contract.task.FileDatasetFileStatus;
import cn.superhuang.data.scalpel.contract.task.FileDatasetInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.FileDatasetInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.FileDatasetParseStatus;
import cn.superhuang.data.scalpel.contract.task.KafkaInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.KafkaInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.KafkaStartingOffsets;
import cn.superhuang.data.scalpel.contract.task.KafkaValueColumn;
import cn.superhuang.data.scalpel.contract.task.KafkaValueSchema;
import cn.superhuang.data.scalpel.contract.task.MetadataModel;
import cn.superhuang.data.scalpel.contract.task.MetadataModelStatus;
import cn.superhuang.data.scalpel.contract.task.ModelInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.ModelInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.RenameColumnMapping;
import cn.superhuang.data.scalpel.contract.task.RenameConfiguration;
import cn.superhuang.data.scalpel.contract.task.RenameNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.TaskCompilationRequest;
import cn.superhuang.data.scalpel.contract.task.TaskCompilationResponse;
import cn.superhuang.data.scalpel.contract.task.TaskType;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.builtin.PostgreSqlDialect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CanvasTaskRunPreparationServiceTest {
    private final DataSourceRepository dataSourceRepository = mock(DataSourceRepository.class);
    private final DataSourceRuntimeService runtimeService = mock(DataSourceRuntimeService.class);
    private final DataModelRepository modelRepository = mock(DataModelRepository.class);
    private final DataModelFieldRepository fieldRepository = mock(DataModelFieldRepository.class);
    private final TaskCompilationService compilationService = mock(TaskCompilationService.class);
    private final ApiResourceService apiResourceService = mock(ApiResourceService.class);
    private final SpatialFeatureResourceService spatialFeatureResourceService =
            mock(SpatialFeatureResourceService.class);
    private final FileDatasetRepository fileDatasetRepository = mock(FileDatasetRepository.class);
    private final FileDatasetTableRepository fileDatasetTableRepository = mock(FileDatasetTableRepository.class);
    private final FileDatasetFileRepository fileDatasetFileRepository = mock(FileDatasetFileRepository.class);
    private final FileDatasetFieldRepository fileDatasetFieldRepository = mock(FileDatasetFieldRepository.class);
    private final FileDatasetTableSourceRepository fileDatasetTableSourceRepository =
            mock(FileDatasetTableSourceRepository.class);
    private final CanvasFileStorageRuntimeProvider fileStorageRuntimeProvider =
            mock(CanvasFileStorageRuntimeProvider.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<CanvasFileStorageRuntimeProvider> fileStorageRuntimeProviderProvider =
            mock(ObjectProvider.class);
    private final DialectRegistry dialectRegistry = new DialectRegistry(List.of(new PostgreSqlDialect()));
    private CanvasTaskRunPreparationService service;
    private DataSource dataSource;
    private DataModel model;
    private DataModelField field;

    @BeforeEach
    void setUp() {
        service = new CanvasTaskRunPreparationService(
                dataSourceRepository,
                runtimeService,
                modelRepository,
                fieldRepository,
                dialectRegistry,
                compilationService,
                apiResourceService,
                spatialFeatureResourceService,
                fileDatasetRepository,
                fileDatasetTableRepository,
                fileDatasetFileRepository,
                fileDatasetFieldRepository,
                fileDatasetTableSourceRepository,
                fileStorageRuntimeProviderProvider,
                new ObjectMapper()
        );
        UUID dataSourceId = UUID.randomUUID();
        dataSource = DataSource.create(
                "warehouse",
                "数仓",
                null,
                Set.of(DataSourcePurpose.STORAGE),
                DataSourceType.POSTGRESQL,
                true,
                null,
                DataSourceConnection.jdbc(
                        "127.0.0.1", 5432, "warehouse", "public", "user", "secret", Map.of()
                )
        );
        setIdentity(dataSource, dataSourceId);
        model = DataModel.create(
                "orders_model",
                "订单模型",
                null,
                dataSourceId,
                "warehouse",
                "public",
                "dwd_orders",
                PhysicalTableMode.MANAGED,
                null
        );
        setIdentity(model, UUID.randomUUID());
        model.publish();
        field = DataModelField.create(
                model.getId(), "id", "主键", PlatformDataType.LONG,
                null, null, null, false, true, 0, "订单主键"
        );
        setIdentity(field, UUID.randomUUID());

        when(modelRepository.findAllById(any())).thenReturn(List.of(model));
        when(fieldRepository.findAllByModelIdInOrderByModelAndSort(any())).thenReturn(List.of(field));
        when(dataSourceRepository.findAllById(any())).thenReturn(List.of(dataSource));
        when(fileStorageRuntimeProviderProvider.getIfAvailable()).thenReturn(fileStorageRuntimeProvider);
        when(fileStorageRuntimeProvider.runtimeStorage()).thenReturn(
                new CanvasTaskRunManifest.RuntimeFileStorage(
                        "http://minio:9000",
                        "us-east-1",
                        "datascalpel",
                        true,
                        "runner-access",
                        "runner-secret"
                )
        );
        when(fileStorageRuntimeProvider.resolveObjectKey(anyString()))
                .thenAnswer(invocation -> "root/" + invocation.getArgument(0, String.class));
        when(compilationService.compile(any())).thenReturn(new TaskCompilationResponse(
                UUID.randomUUID(),
                TaskType.CANVAS,
                true,
                1,
                "local-test",
                List.of(),
                List.of()
        ));
    }

    @Test
    void buildsOrderedLogicalModelSnapshotAndPrimaryKey() {
        CanvasTaskRunPreparationService.Preparation preparation = service.prepare(definition());

        MetadataModel snapshot = preparation.metadataSnapshot().models().getFirst();
        assertThat(snapshot.id()).isEqualTo(model.getId());
        assertThat(snapshot.code()).isEqualTo("orders_model");
        assertThat(snapshot.status()).isEqualTo(MetadataModelStatus.PUBLISHED);
        assertThat(snapshot.dataSourceId()).isEqualTo(dataSource.getId());
        assertThat(snapshot.columns()).singleElement().satisfies(column -> {
            assertThat(column.name()).isEqualTo("id");
            assertThat(column.fieldType()).isEqualTo(PlatformDataType.LONG);
            assertThat(column.defaultValue()).isNull();
            assertThat(column.autoIncrement()).isFalse();
            assertThat(column.generated()).isFalse();
            assertThat(column.comment()).isEqualTo("订单主键");
        });
        assertThat(snapshot.uniqueKeys()).singleElement().satisfies(key -> {
            assertThat(key.name()).isEqualTo("MODEL_PRIMARY_KEY");
            assertThat(key.type()).isEqualTo(MetadataUniqueKeyType.PRIMARY_KEY);
            assertThat(key.columns()).containsExactly("id");
        });
        assertThat(preparation.metadataSnapshot().dataSources()).singleElement()
                .satisfies(source -> assertThat(source.tables()).isEmpty());
        assertThat(preparation.runtimeDataSources()).singleElement()
                .satisfies(source -> assertThat(source.purposes())
                        .containsExactly(cn.superhuang.data.scalpel.contract.task.DataSourcePurpose.SOURCE));
        assertThat(preparation.modelVersions()).containsKey(model.getId());

        model.advanceSchemaVersion();
        assertThatThrownBy(() -> service.assertModelsUnchanged(preparation.modelVersions()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Canvas 引用的模型已变化");
    }

    @Test
    void rejectsStorageOnlyDataSourceForJdbcOutput() {
        assertThatThrownBy(() -> service.prepare(jdbcOutputDefinition(dataSource.getId())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("输出数据源不具有 DISTRIBUTION 用途");
    }

    @Test
    void carriesGeometryModelDefinitionIntoAuthoritativeSnapshot() {
        DataModelField geometryField = DataModelField.create(
                model.getId(),
                "shape",
                "空间位置",
                PlatformDataType.GEOMETRY,
                null,
                null,
                null,
                new GeometryTypeDefinition(
                        GeometryKind.POINT,
                        CrsReference.epsg(4326),
                        CoordinateDimension.XY
                ),
                true,
                false,
                1,
                null
        );
        setIdentity(geometryField, UUID.randomUUID());
        when(fieldRepository.findAllByModelIdInOrderByModelAndSort(any()))
                .thenReturn(List.of(field, geometryField));
        CanvasTaskRunPreparationService.Preparation preparation = service.prepare(definition());

        assertThat(preparation.metadataSnapshot().models()).singleElement().satisfies(snapshot ->
                assertThat(snapshot.columns()).filteredOn(column ->
                        column.fieldType() == PlatformDataType.GEOMETRY)
                        .singleElement()
                        .satisfies(column -> assertThat(column.geometry())
                                .isEqualTo(geometryField.getGeometry())));
        verify(compilationService).compile(any());
    }

    @Test
    void preparesKafkaInlineSchemaWithoutLoadingAnyModel() {
        DataSource kafka = DataSource.create(
                "event-bus",
                "事件 Kafka",
                null,
                Set.of(DataSourcePurpose.SOURCE, DataSourcePurpose.DISTRIBUTION),
                DataSourceType.KAFKA,
                true,
                null,
                DataSourceConnection.nonJdbc(
                        "kafka.internal:9092",
                        null,
                        null,
                        "canvas",
                        "secret",
                        Map.of("securityProtocol", "SASL_SSL", "saslMechanism", "PLAIN")
                )
        );
        setIdentity(kafka, UUID.randomUUID());
        when(dataSourceRepository.findAllById(any())).thenReturn(List.of(kafka));
        CanvasDefinition definition = new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(new KafkaInputNodeDefinition(
                        UUID.randomUUID().toString(),
                        "事件输入",
                        new CanvasNodeLayout(0d, 0d, 240d, 120d),
                        new KafkaInputConfiguration(
                                kafka.getId().toString(),
                                "order-events",
                                new KafkaValueSchema(List.of(
                                        new KafkaValueColumn(
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
                                KafkaStartingOffsets.LATEST
                        )
                )),
                List.of()
        );

        CanvasTaskRunPreparationService.Preparation preparation = service.prepare(
                definition,
                CanvasExecutionMode.STREAMING
        );

        assertThat(preparation.metadataSnapshot().models()).isEmpty();
        assertThat(preparation.modelVersions()).isEmpty();
        assertThat(preparation.metadataSnapshot().dataSources()).singleElement().satisfies(source -> {
            assertThat(source.connectionKind()).isEqualTo(ConnectionKind.KAFKA);
            assertThat(source.tables()).isEmpty();
        });
        ArgumentCaptor<TaskCompilationRequest> requestCaptor =
                ArgumentCaptor.forClass(TaskCompilationRequest.class);
        verify(compilationService).compile(requestCaptor.capture());
        KafkaInputNodeDefinition compiled =
                (KafkaInputNodeDefinition) requestCaptor.getValue()
                        .task().definition().nodes().getFirst();
        assertThat(compiled.configuration().valueSchema().columns()).singleElement().satisfies(column -> {
            assertThat(column.name()).isEqualTo("event_id");
            assertThat(column.fieldType()).isEqualTo(PlatformDataType.LONG);
        });
        verify(modelRepository, never()).findAllById(any());
        verify(fieldRepository, never()).findAllByModelIdInOrderByModelAndSort(any());
    }

    @Test
    void allowsPreparationWhenCompilerReportsWarningsOnly() {
        when(compilationService.compile(any())).thenReturn(compilation(
                true,
                new CompilationIssue(
                        "COLUMN_CAST_RISK",
                        CompilationSeverity.WARNING,
                        "字段转换可能因实际数据失败",
                        "output-node",
                        "configuration.columnMappings[0]"
                )
        ));

        CanvasTaskRunPreparationService.Preparation preparation = service.prepare(definition());

        assertThat(preparation.compilation().valid()).isTrue();
        assertThat(preparation.compilation().canvasIssues())
                .singleElement()
                .extracting(CompilationIssue::severity)
                .isEqualTo(CompilationSeverity.WARNING);
    }

    @Test
    void rejectsPreparationWhenCompilerReportsAnError() {
        when(compilationService.compile(any())).thenReturn(compilation(
                false,
                new CompilationIssue(
                        "SPARK_ANALYSIS_ERROR",
                        CompilationSeverity.ERROR,
                        "Spark Schema 分析失败",
                        "join-node",
                        "configuration"
                )
        ));

        assertThatThrownBy(() -> service.prepare(definition()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Spark Schema 分析失败");
    }

    @Test
    void preservesRenameConfigurationInTheTaskEngineCompilationContract() {
        String inputId = UUID.randomUUID().toString();
        String renameId = UUID.randomUUID().toString();
        CanvasDefinition renameDefinition = new CanvasDefinition(
                1,
                2,
                List.of(
                        new ModelInputNodeDefinition(
                                inputId,
                                "订单模型输入",
                                new CanvasNodeLayout(0d, 0d, 240d, 120d),
                                new ModelInputConfiguration(model.getId().toString())
                        ),
                        new RenameNodeDefinition(
                                renameId,
                                "订单重命名",
                                new CanvasNodeLayout(320d, 0d, 240d, 120d),
                                new RenameConfiguration(
                                        "orders_model",
                                        "source_orders",
                                        List.of(new RenameColumnMapping("id", "order_id"))
                                )
                        )
                ),
                List.of(new CanvasEdgeDefinition(
                        UUID.randomUUID().toString(),
                        inputId,
                        renameId
                ))
        );

        service.prepare(renameDefinition);

        ArgumentCaptor<TaskCompilationRequest> request = ArgumentCaptor.forClass(TaskCompilationRequest.class);
        verify(compilationService).compile(request.capture());
        RenameNodeDefinition rename = (RenameNodeDefinition)
                request.getValue().task().definition().nodes().get(1);
        assertThat(rename.configuration().sourceTableName()).isEqualTo("orders_model");
        assertThat(rename.configuration().outputTableName()).isEqualTo("source_orders");
        assertThat(rename.configuration().columnMappings()).singleElement()
                .satisfies(mapping -> {
                    assertThat(mapping.sourceColumnName()).isEqualTo("id");
                    assertThat(mapping.targetColumnName()).isEqualTo("order_id");
                });
    }

    @Test
    void buildsAuthoritativeFileMetadataAndPrivateManifestInput() {
        FileDataset dataset = FileDataset.create(
                null,
                "订单归档",
                FileDatasetType.PARQUET,
                "{\"kind\":\"PARQUET\"}",
                null
        );
        setIdentity(dataset, UUID.randomUUID());
        FileDatasetFile file = FileDatasetFile.create(
                dataset.getId(),
                "orders.parquet",
                FileDatasetFormat.PARQUET,
                FileDatasetCompression.NONE,
                "datasets/orders.parquet",
                "application/octet-stream",
                1024,
                "etag-1"
        );
        setIdentity(file, UUID.randomUUID());
        FileDatasetTable table = FileDatasetTable.create(
                dataset.getId(), "orders_file", "订单文件表"
        );
        setIdentity(table, UUID.randomUUID());
        FileDatasetTableSource source = FileDatasetTableSource.create(
                table.getId(), file.getId(), "orders.parquet", "FILE",
                0, 10, "a".repeat(64), "{}", Instant.now()
        );
        setIdentity(source, UUID.randomUUID());
        UUID parseJobId = UUID.randomUUID();
        table.queueInitialLoad(parseJobId);
        table.startLoad(parseJobId);
        table.completeInitialLoad(parseJobId, "{}", true);

        FileDatasetFile appendedFile = FileDatasetFile.create(
                dataset.getId(),
                "orders-2.parquet",
                FileDatasetFormat.PARQUET,
                FileDatasetCompression.NONE,
                "datasets/orders-2.parquet",
                "application/octet-stream",
                2048,
                "etag-2"
        );
        setIdentity(appendedFile, UUID.randomUUID());
        FileDatasetTableSource appendedSource = FileDatasetTableSource.create(
                table.getId(), appendedFile.getId(), "orders-2.parquet", "FILE",
                1, 5, "a".repeat(64), "{}", Instant.now()
        );
        setIdentity(appendedSource, UUID.randomUUID());

        FileDatasetField fileField = FileDatasetField.create(
                table.getId(),
                "order_id",
                0,
                new PlatformTypeDefinition(PlatformDataType.LONG, null, null, null),
                false
        );
        setIdentity(fileField, UUID.randomUUID());

        when(modelRepository.findAllById(any())).thenReturn(List.of());
        when(fieldRepository.findAllByModelIdInOrderByModelAndSort(any())).thenReturn(List.of());
        when(dataSourceRepository.findAllById(any())).thenReturn(List.of());
        when(fileDatasetTableRepository.findAllById(any())).thenReturn(List.of(table));
        when(fileDatasetRepository.findAllById(any())).thenReturn(List.of(dataset));
        when(fileDatasetFileRepository.findAllById(any())).thenReturn(List.of(appendedFile, file));
        when(fileDatasetTableSourceRepository
                .findByFileDatasetTableIdInOrderByFileDatasetTableIdAscSourceOrderAsc(any()))
                .thenReturn(List.of(source, appendedSource));
        when(fileDatasetFieldRepository
                .findByFileDatasetTableIdInOrderByFileDatasetTableIdAscSortOrderAsc(any()))
                .thenReturn(List.of(fileField));

        CanvasTaskRunPreparationService.Preparation preparation =
                service.prepare(fileDefinition(table.getId()));

        assertThat(preparation.metadataSnapshot().fileDatasetTables()).singleElement().satisfies(metadata -> {
            assertThat(metadata.id()).isEqualTo(table.getId());
            assertThat(metadata.code()).isEqualTo("orders_file");
            assertThat(metadata.parseStatus())
                    .isEqualTo(FileDatasetParseStatus.READY);
            assertThat(metadata.fileStatus())
                    .isEqualTo(FileDatasetFileStatus.READY);
            assertThat(metadata.columns()).singleElement().satisfies(column -> {
                assertThat(column.name()).isEqualTo("order_id");
                assertThat(column.fieldType()).isEqualTo(PlatformDataType.LONG);
            });
        });
        assertThat(preparation.runtimeFileStorage()).isNotNull();
        assertThat(preparation.runtimeFileInputs()).singleElement().satisfies(input -> {
            assertThat(input.fileDatasetTableId()).isEqualTo(table.getId());
            assertThat(input.schemaFingerprint()).hasSize(64);
            assertThat(input.sources())
                    .extracting(
                            CanvasTaskRunManifest.RuntimeFileSource::tableSourceId,
                            CanvasTaskRunManifest.RuntimeFileSource::objectKey
                    )
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(
                                    source.getId(), "root/datasets/orders.parquet"),
                            org.assertj.core.groups.Tuple.tuple(
                                    appendedSource.getId(), "root/datasets/orders-2.parquet")
                    );
        });
    }

    private CanvasDefinition definition() {
        return new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(new ModelInputNodeDefinition(
                        UUID.randomUUID().toString(),
                        "订单模型输入",
                        new CanvasNodeLayout(0d, 0d, 240d, 120d),
                        new ModelInputConfiguration(model.getId().toString())
                )),
                List.of()
        );
    }

    private static CanvasDefinition jdbcOutputDefinition(UUID dataSourceId) {
        return new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(new JdbcOutputNodeDefinition(
                        UUID.randomUUID().toString(),
                        "订单 JDBC 输出",
                        new CanvasNodeLayout(0d, 0d, 240d, 120d),
                        new JdbcOutputConfiguration(
                                "orders",
                                dataSourceId.toString(),
                                "dwd_orders",
                                JdbcWriteMode.APPEND,
                                List.of(new JdbcColumnMapping("order_id", "order_id")),
                                List.of()
                        )
                )),
                List.of()
        );
    }

    private static CanvasDefinition fileDefinition(UUID tableId) {
        return new CanvasDefinition(
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                List.of(new FileDatasetInputNodeDefinition(
                        UUID.randomUUID().toString(),
                        "订单文件输入",
                        new CanvasNodeLayout(0d, 0d, 240d, 120d),
                        new FileDatasetInputConfiguration(tableId.toString())
                )),
                List.of()
        );
    }

    private static TaskCompilationResponse compilation(
            boolean valid,
            CompilationIssue issue
    ) {
        return new TaskCompilationResponse(
                UUID.randomUUID(),
                TaskType.CANVAS,
                valid,
                1,
                "local-test",
                List.of(issue),
                List.of()
        );
    }

    private static void setIdentity(Object entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
        ReflectionTestUtils.setField(entity, "createdAt", Instant.parse("2026-07-22T00:00:00Z"));
        ReflectionTestUtils.setField(entity, "updatedAt", Instant.parse("2026-07-22T00:00:00Z"));
    }
}
