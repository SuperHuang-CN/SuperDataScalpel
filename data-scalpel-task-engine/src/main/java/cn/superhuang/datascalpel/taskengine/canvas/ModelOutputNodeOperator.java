package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasJdbcDatabaseType;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.ConnectionKind;
import cn.superhuang.data.scalpel.contract.task.DataSourcePurpose;
import cn.superhuang.data.scalpel.contract.task.JdbcWriteMode;
import cn.superhuang.data.scalpel.contract.task.MetadataModelPhysicalTableMode;
import cn.superhuang.data.scalpel.contract.task.MetadataModelStatus;
import cn.superhuang.data.scalpel.contract.task.MetadataUniqueKey;
import cn.superhuang.data.scalpel.contract.task.MetadataUniqueKeyType;
import cn.superhuang.data.scalpel.contract.task.ModelOutputConfiguration;
import cn.superhuang.data.scalpel.contract.task.ModelOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ModelOutputWrite;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ModelOutputNodeOperator implements CanvasNodeOperator {
    private final OutputColumnMappingOperator mappingOperator = new OutputColumnMappingOperator();

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.MODEL_OUTPUT;
    }

    @Override
    public CanvasNodeCategory category() {
        return CanvasNodeCategory.OUTPUT;
    }

    @Override
    public Set<CanvasExecutionMode> supportedModes() {
        return Set.of(CanvasExecutionMode.BATCH, CanvasExecutionMode.STREAMING);
    }

    @Override
    public CanvasNodeOperationResult apply(
            CanvasNodeDefinition definition,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeOperationContext context
    ) {
        if (!(definition instanceof ModelOutputNodeDefinition node)) {
            throw new IllegalArgumentException("MODEL_OUTPUT operator received " + definition.nodeType());
        }
        ModelOutputConfiguration configuration = node.configuration();
        if (configuration == null) {
            return CanvasNodeOperationResult.outputOnly();
        }
        CanvasNodeIssueSink issues = context.issues();
        if (configuration.writes().isEmpty()) {
            issues.error("REQUIRED_CONFIGURATION", "至少配置一条写入", "configuration.writes");
            return CanvasNodeOperationResult.outputOnly();
        }
        if (!CanvasNodeSupport.validateOutputWriteIds(
                configuration.writes(), write -> write.writeId(), issues)) {
            return CanvasNodeOperationResult.outputOnly();
        }
        if (context.executionMode() == CanvasExecutionMode.STREAMING && configuration.writes().size() > 32) {
            issues.error("OUTPUT_WRITE_COUNT_EXCEEDED", "实时输出最多支持 32 条写入", "configuration.writes");
            return CanvasNodeOperationResult.outputOnly();
        }
        if (configuration.writes().size() != 1) {
            List<CanvasPreparedOutput> prepared = new ArrayList<>();
            List<CanvasLineageOutputCandidate> lineage = new ArrayList<>();
            for (var write : configuration.writes()) {
                CanvasNodeOperationResult item = apply(new ModelOutputNodeDefinition(
                        node.id(), node.name(), node.layout(), new ModelOutputConfiguration(List.of(write))), inputs, context);
                prepared.addAll(item.preparedOutputs());
                lineage.addAll(item.lineageOutputCandidates());
            }
            return issues.hasErrors() ? CanvasNodeOperationResult.outputOnly()
                    : CanvasNodeOperationResult.outputs(prepared, lineage);
        }
        ModelOutputWrite write = configuration.writes().iterator().next();
        CanvasNodeSupport.required(
                write.sourceTableName(),
                "请选择来源表",
                "configuration.sourceTableName",
                issues
        );
        UUID modelId = CanvasNodeSupport.parseModelUuid(
                write.targetModelId(),
                "configuration.targetModelId",
                issues
        );
        if (write.writeMode() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择写入模式", "configuration.writeMode");
        } else if (context.executionMode() == CanvasExecutionMode.STREAMING
                && write.writeMode() == JdbcWriteMode.OVERWRITE) {
            issues.error(
                    "STREAMING_MODEL_OUTPUT_OVERWRITE_NOT_SUPPORTED",
                    "实时 MODEL_OUTPUT 不支持 OVERWRITE",
                    "configuration.writeMode"
            );
        }
        if (write.columnMappings() == null) {
            issues.error("REQUIRED_CONFIGURATION", "字段映射列表不能为空", "configuration.columnMappings");
        }

        SparkCanvasTable source = inputs.get(write.sourceTableName());
        if (!CanvasNodeSupport.blank(write.sourceTableName()) && source == null) {
            issues.error(
                    "TABLE_NOT_FOUND",
                    "来源表不在上游数据中：" + write.sourceTableName(),
                    "configuration.sourceTableName"
            );
        }
        MetadataIndex.ModelEntry model =
                modelId == null ? null : context.metadataIndex().model(modelId);
        if (modelId != null && model == null) {
            issues.error("MODEL_NOT_FOUND", "目标模型不存在：" + modelId, "configuration.targetModelId");
        }
        if (model != null && model.metadata().status() != MetadataModelStatus.PUBLISHED) {
            issues.error(
                    "MODEL_NOT_PUBLISHED",
                    "目标模型不是已发布状态：" + model.metadata().name(),
                    "configuration.targetModelId"
            );
        }
        MetadataIndex.DataSourceEntry dataSource = model == null
                ? null
                : context.metadataIndex().dataSource(model.metadata().dataSourceId());
        if (model != null && !availableForWrite(dataSource)) {
            issues.error(
                    "MODEL_DATA_SOURCE_UNAVAILABLE",
                    "目标模型数据源不存在、未启用或不具有 STORAGE 用途",
                    "configuration.targetModelId"
            );
        }
        if (dataSource != null) {
            CanvasNodeSupport.validateJdbcWriteMode(
                    write.writeMode(),
                    dataSource.metadata().jdbcDatabaseType(),
                    "configuration.writeMode",
                    issues
            );
        }
        if (model != null
                && write.writeMode() == JdbcWriteMode.OVERWRITE
                && model.metadata().physicalTableMode() != MetadataModelPhysicalTableMode.MANAGED) {
            issues.error(
                    "OVERWRITE_REQUIRES_MANAGED_MODEL",
                    "OVERWRITE 只允许写入 MANAGED 模型",
                    "configuration.writeMode"
            );
        }
        List<String> upsertKeyColumns = write.writeMode() == JdbcWriteMode.UPSERT
                ? validateUpsertConfiguration(model, dataSource, issues)
                : List.of();
        if (source == null || model == null || issues.hasErrors()) {
            return CanvasNodeOperationResult.outputOnly();
        }

        CanvasTableSchema targetSchema = new CanvasTableSchema(
                model.metadata().code(),
                model.tableSchema().origin(),
                model.metadata().columns()
        );
        CanvasNodeSupport.validateSupportedGeometry(
                targetSchema.columns(),
                "configuration.targetModelId",
                issues
        );
        if (write.writeMode() == JdbcWriteMode.UPSERT) {
            Set<String> mappedTargets = write.columnMappings().stream()
                    .filter(java.util.Objects::nonNull)
                    .map(cn.superhuang.data.scalpel.contract.task.JdbcColumnMapping::targetColumnName)
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet());
            if (!mappedTargets.containsAll(upsertKeyColumns)) {
                issues.error(
                        "UPSERT_KEY_NOT_MAPPED",
                        "目标模型主键必须全部映射到输出字段",
                        "configuration.columnMappings"
                );
            }
        }
        if (issues.hasErrors()) {
            return CanvasNodeOperationResult.outputOnly();
        }
        Dataset<Row> selected = mappingOperator.apply(
                source,
                targetSchema,
                write.columnMappings(),
                issues
        );
        if (selected == null || issues.hasErrors()) {
            return CanvasNodeOperationResult.outputOnly();
        }
        Set<String> projectedColumns = Set.of(selected.columns());
        List<cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema> projectedSchemas =
                targetSchema.columns().stream()
                        .filter(column -> projectedColumns.contains(column.name()))
                        .toList();
        if (context.executionMode() == CanvasExecutionMode.STREAMING
                && projectedSchemas.stream().anyMatch(column ->
                column.fieldType() == cn.superhuang.data.scalpel.contract.type.PlatformDataType.GEOMETRY)) {
            issues.error(
                    "SPATIAL_JDBC_UNSUPPORTED",
                    "第一阶段不支持实时任务写入 Geometry",
                    "configuration.columnMappings"
            );
            return CanvasNodeOperationResult.outputOnly();
        }
        CanvasNodeSupport.validateJdbcGeometryDatabase(
                projectedSchemas,
                dataSource.metadata().jdbcDatabaseType(),
                "configuration.columnMappings",
                issues
        );
        if (issues.hasErrors()) {
            return CanvasNodeOperationResult.outputOnly();
        }
        CanvasPreparedOutput prepared =
                context.dataAccess().prepareModelOutput(
                        node, write, model, targetSchema, selected, upsertKeyColumns);
        var lineageWriteMode = switch (write.writeMode()) {
            case APPEND -> cn.superhuang.data.scalpel.contract.task.CanvasLineageCompilation.WriteMode.APPEND;
            case OVERWRITE -> cn.superhuang.data.scalpel.contract.task.CanvasLineageCompilation.WriteMode.FULL_OVERWRITE;
            case UPSERT -> cn.superhuang.data.scalpel.contract.task.CanvasLineageCompilation.WriteMode.UPSERT;
        };
        return CanvasNodeOperationResult.output(
                prepared,
                CanvasLineageOutputCandidate.model(
                        node, selected, model.metadata(), targetSchema, lineageWriteMode,
                        write.writeId()
                )
        );
    }

    private static List<String> validateUpsertConfiguration(
            MetadataIndex.ModelEntry model,
            MetadataIndex.DataSourceEntry dataSource,
            CanvasNodeIssueSink issues
    ) {
        if (model == null || dataSource == null) {
            return List.of();
        }
        CanvasJdbcDatabaseType databaseType = dataSource.metadata().jdbcDatabaseType();
        if (databaseType != CanvasJdbcDatabaseType.POSTGRESQL
                && databaseType != CanvasJdbcDatabaseType.HIGHGO
                && databaseType != CanvasJdbcDatabaseType.MYSQL
                && databaseType != CanvasJdbcDatabaseType.OPENGAUSS
                && databaseType != CanvasJdbcDatabaseType.KINGBASE
                && databaseType != CanvasJdbcDatabaseType.DAMENG
                && databaseType != CanvasJdbcDatabaseType.ORACLE
                && databaseType != CanvasJdbcDatabaseType.SQL_SERVER) {
            issues.error(
                    "UPSERT_DATABASE_NOT_SUPPORTED",
                    "当前目标数据库未开放 UPSERT",
                    "configuration.targetModelId"
            );
        }
        MetadataUniqueKey primaryKey = model.metadata().uniqueKeys().stream()
                .filter(key -> key.type() == MetadataUniqueKeyType.PRIMARY_KEY)
                .findFirst()
                .orElse(null);
        if (primaryKey == null || primaryKey.columns().isEmpty()) {
            issues.error(
                    "UPSERT_KEY_REQUIRED",
                    "目标模型必须定义主键才能使用 UPSERT",
                    "configuration.targetModelId"
            );
            return List.of();
        }
        List<String> primaryKeyColumns = primaryKey.columns();
        if (model.metadata().fields() != null && !model.metadata().fields().isEmpty()) {
            Map<String, Integer> fieldOrder = model.metadata().fields().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            cn.superhuang.data.scalpel.contract.task.MetadataModelField::code,
                            cn.superhuang.data.scalpel.contract.task.MetadataModelField::sortOrder
                    ));
            primaryKeyColumns = primaryKey.columns().stream()
                    .sorted(java.util.Comparator.comparingInt(
                            column -> fieldOrder.getOrDefault(column, Integer.MAX_VALUE)))
                    .toList();
        }
        Map<String, cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema> columns =
                model.metadata().columns().stream().collect(java.util.stream.Collectors.toMap(
                        cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema::name,
                        java.util.function.Function.identity()
                ));
        for (String key : primaryKeyColumns) {
            var column = columns.get(key);
            if (column == null || column.autoIncrement() || column.generated()
                    || column.fieldType() == PlatformDataType.GEOMETRY) {
                issues.error(
                        "UPSERT_KEY_COLUMN_NOT_ALLOWED",
                        "模型主键字段不存在或不能用于 UPSERT：" + key,
                        "configuration.targetModelId"
                );
            }
        }
        return primaryKeyColumns;
    }

    private static boolean availableForWrite(MetadataIndex.DataSourceEntry dataSource) {
        return dataSource != null
                && dataSource.metadata().enabled()
                && dataSource.metadata().connectionKind() == ConnectionKind.JDBC
                && dataSource.metadata().purposes().contains(DataSourcePurpose.STORAGE);
    }
}
