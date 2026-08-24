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
import cn.superhuang.data.scalpel.contract.task.DatabaseObjectType;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputConfiguration;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputWrite;
import cn.superhuang.data.scalpel.contract.task.MetadataTable;
import cn.superhuang.data.scalpel.contract.task.MetadataUniqueKey;
import cn.superhuang.data.scalpel.contract.task.JdbcWriteMode;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class JdbcOutputNodeOperator implements CanvasNodeOperator {
    private final OutputColumnMappingOperator mappingOperator = new OutputColumnMappingOperator();

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.JDBC_OUTPUT;
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
        if (!(definition instanceof JdbcOutputNodeDefinition node)) {
            throw new IllegalArgumentException("JDBC_OUTPUT operator received " + definition.nodeType());
        }
        JdbcOutputConfiguration configuration = node.configuration();
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
                CanvasNodeOperationResult item = apply(new JdbcOutputNodeDefinition(
                        node.id(), node.name(), node.layout(), new JdbcOutputConfiguration(
                        configuration.dataSourceId(), List.of(write))), inputs, context);
                prepared.addAll(item.preparedOutputs());
                lineage.addAll(item.lineageOutputCandidates());
            }
            return issues.hasErrors() ? CanvasNodeOperationResult.outputOnly()
                    : CanvasNodeOperationResult.outputs(prepared, lineage);
        }
        JdbcOutputWrite write = configuration.writes().iterator().next();
        CanvasNodeSupport.required(
                write.sourceTableName(),
                "请选择来源表",
                "configuration.sourceTableName",
                issues
        );
        UUID dataSourceId = CanvasNodeSupport.parseUuid(
                configuration.dataSourceId(), "configuration.dataSourceId", issues);
        CanvasNodeSupport.required(
                write.targetTableName(),
                "请选择目标表",
                "configuration.targetTableName",
                issues
        );
        if (write.writeMode() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择写入模式", "configuration.writeMode");
        }
        if (write.columnMappings() == null) {
            issues.error("REQUIRED_CONFIGURATION", "字段映射列表不能为空", "configuration.columnMappings");
        }
        if (write.upsertKeyColumns() == null) {
            issues.error("REQUIRED_CONFIGURATION", "UPSERT Key 必须是数组", "configuration.upsertKeyColumns");
        } else if (write.writeMode() != JdbcWriteMode.UPSERT
                && !write.upsertKeyColumns().isEmpty()) {
            issues.error(
                    "UPSERT_KEY_NOT_ALLOWED",
                    "非 UPSERT 模式不能保存 UPSERT Key",
                    "configuration.upsertKeyColumns"
            );
        }

        SparkCanvasTable source = inputs.get(write.sourceTableName());
        if (!CanvasNodeSupport.blank(write.sourceTableName()) && source == null) {
            issues.error(
                    "TABLE_NOT_FOUND",
                    "来源表不在上游数据中：" + write.sourceTableName(),
                    "configuration.sourceTableName"
            );
        }
        MetadataIndex.DataSourceEntry dataSource =
                dataSourceId == null ? null : context.metadataIndex().dataSource(dataSourceId);
        if (dataSourceId != null && (dataSource == null
                || !dataSource.metadata().enabled()
                || dataSource.metadata().connectionKind() != ConnectionKind.JDBC
                || !dataSource.metadata().purposes().contains(DataSourcePurpose.DISTRIBUTION))) {
            issues.error(
                    "DATA_SOURCE_UNAVAILABLE",
                    "目标数据源不存在、未启用或不具有 DISTRIBUTION 用途",
                    "configuration.dataSourceId"
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
        MetadataTable target =
                dataSource == null ? null : dataSource.table(write.targetTableName());
        if (!CanvasNodeSupport.blank(write.targetTableName()) && target == null) {
            issues.error(
                    "TABLE_NOT_FOUND",
                    "目标表不存在：" + write.targetTableName(),
                    "configuration.targetTableName"
            );
        } else if (target != null && target.objectType() != DatabaseObjectType.TABLE) {
            issues.error(
                    "TARGET_TABLE_NOT_WRITABLE",
                    "JDBC 输出目标必须是物理表",
                    "configuration.targetTableName"
            );
        }
        if (write.writeMode() == JdbcWriteMode.UPSERT && target != null && dataSource != null) {
            validateUpsertConfiguration(write, dataSource, target, issues);
        }
        if (source == null || target == null || issues.hasErrors()) {
            return CanvasNodeOperationResult.outputOnly();
        }

        CanvasTableSchema targetSchema =
                new CanvasTableSchema(target.tableName(), null, target.columns());
        CanvasNodeSupport.validateSupportedGeometry(
                targetSchema.columns(),
                "configuration.targetTableName",
                issues
        );
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
        if (write.writeMode() == JdbcWriteMode.UPSERT) {
            if (!projectedColumns.containsAll(write.upsertKeyColumns())) {
                issues.error(
                        "UPSERT_KEY_NOT_MAPPED",
                        "UPSERT Key 必须全部映射到目标字段",
                        "configuration.upsertKeyColumns"
                );
                return CanvasNodeOperationResult.outputOnly();
            }
        }
        CanvasPreparedOutput prepared =
                context.dataAccess().prepareJdbcOutput(node, write, targetSchema, selected);
        var lineageWriteMode = switch (write.writeMode()) {
            case APPEND -> cn.superhuang.data.scalpel.contract.task.CanvasLineageCompilation.WriteMode.APPEND;
            case OVERWRITE -> cn.superhuang.data.scalpel.contract.task.CanvasLineageCompilation.WriteMode.FULL_OVERWRITE;
            case UPSERT -> cn.superhuang.data.scalpel.contract.task.CanvasLineageCompilation.WriteMode.UPSERT;
        };
        return CanvasNodeOperationResult.output(
                prepared,
                CanvasLineageOutputCandidate.jdbcTable(
                        node, selected, dataSourceId, target, lineageWriteMode,
                        write.writeId())
        );
    }

    private static void validateUpsertConfiguration(
            JdbcOutputWrite write,
            MetadataIndex.DataSourceEntry dataSource,
            MetadataTable target,
            CanvasNodeIssueSink issues
    ) {
        if (dataSource.metadata().jdbcDatabaseType() != CanvasJdbcDatabaseType.POSTGRESQL
                && dataSource.metadata().jdbcDatabaseType() != CanvasJdbcDatabaseType.MYSQL) {
            issues.error(
                    "UPSERT_DATABASE_NOT_SUPPORTED",
                    "UPSERT 只支持 PostgreSQL 和 MySQL",
                    "configuration.dataSourceId"
            );
        }
        List<String> keys = write.upsertKeyColumns();
        if (keys == null || keys.isEmpty()) {
            issues.error(
                    "UPSERT_KEY_REQUIRED",
                    "UPSERT 必须选择一组主键或唯一索引",
                    "configuration.upsertKeyColumns"
            );
            return;
        }
        MetadataUniqueKey matched = target.uniqueKeys().stream()
                .filter(key -> key.columns().equals(keys))
                .findFirst()
                .orElse(null);
        if (matched == null) {
            issues.error(
                    "UPSERT_KEY_NOT_UNIQUE_CONSTRAINT",
                    "UPSERT Key 必须完整匹配目标表的主键或安全唯一索引",
                    "configuration.upsertKeyColumns"
            );
            return;
        }
        Map<String, cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema> columns =
                target.columns().stream().collect(java.util.stream.Collectors.toMap(
                        cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema::name,
                        java.util.function.Function.identity()
                ));
        for (String key : keys) {
            var column = columns.get(key);
            if (column == null || column.autoIncrement() || column.generated()
                    || column.fieldType() == PlatformDataType.GEOMETRY) {
                issues.error(
                        "UPSERT_KEY_COLUMN_NOT_ALLOWED",
                        "UPSERT Key 不能是自增、生成或 Geometry 字段：" + key,
                        "configuration.upsertKeyColumns"
                );
            }
        }
        if (dataSource.metadata().jdbcDatabaseType() == CanvasJdbcDatabaseType.MYSQL
                && target.uniqueKeys().size() > 1) {
            issues.warning(
                    "MYSQL_UPSERT_MULTIPLE_UNIQUE_KEYS",
                    "MySQL 会在任意唯一键冲突时触发更新，不仅限于已选 Key",
                    "configuration.upsertKeyColumns"
            );
        }
    }
}
