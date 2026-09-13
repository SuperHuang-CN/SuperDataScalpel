package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.*;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class JdbcIncrementalInputNodeOperator implements CanvasNodeOperator {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.JDBC_INCREMENTAL_INPUT;
    }

    @Override
    public CanvasNodeCategory category() {
        return CanvasNodeCategory.INPUT;
    }

    @Override
    public Set<CanvasExecutionMode> supportedModes() {
        return Set.of(CanvasExecutionMode.STREAMING);
    }

    @Override
    public CanvasNodeOperationResult apply(
            CanvasNodeDefinition definition,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeOperationContext context
    ) {
        if (!(definition instanceof JdbcIncrementalInputNodeDefinition node)) {
            throw new IllegalArgumentException("JDBC_INCREMENTAL_INPUT operator received " + definition.nodeType());
        }
        JdbcIncrementalInputConfiguration configuration = node.configuration();
        if (configuration == null) return CanvasNodeOperationResult.invalid(List.of());
        CanvasNodeIssueSink issues = context.issues();
        UUID dataSourceId = CanvasNodeSupport.parseUuid(
                configuration.dataSourceId(), "configuration.dataSourceId", issues);
        CanvasNodeSupport.required(configuration.tableName(), "请选择物理表", "configuration.tableName", issues);
        CanvasNodeSupport.required(
                configuration.outputTableName(), "请输入输出表名", "configuration.outputTableName", issues);
        CanvasNodeSupport.required(
                configuration.incrementalTimeColumn(), "请选择增量时间字段",
                "configuration.incrementalTimeColumn", issues);
        if (configuration.startPosition() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择首次启动位置", "configuration.startPosition");
        } else if (configuration.startPosition() == JdbcIncrementalStartPosition.AT_TIME
                && configuration.startTime() == null) {
            issues.error("REQUIRED_CONFIGURATION", "AT_TIME 需要起始时间", "configuration.startTime");
        }
        try {
            ZoneId.of(configuration.cursorTimeZone());
        } catch (DateTimeException exception) {
            issues.error("INVALID_CURSOR_TIME_ZONE", "游标时区无效", "configuration.cursorTimeZone");
        }
        if (configuration.visibilityDelaySeconds() == null
                || configuration.visibilityDelaySeconds() < 0
                || configuration.visibilityDelaySeconds() > 3600) {
            issues.error(
                    "INVALID_VISIBILITY_DELAY", "可见性延迟必须在 0 到 3600 秒之间",
                    "configuration.visibilityDelaySeconds");
        }
        if (configuration.triggerIntervalSeconds() == null
                || configuration.triggerIntervalSeconds() < 1
                || configuration.triggerIntervalSeconds() > 300) {
            issues.error(
                    "INVALID_TRIGGER_INTERVAL", "轮询间隔必须在 1 到 300 秒之间",
                    "configuration.triggerIntervalSeconds");
        }
        MetadataIndex.DataSourceEntry dataSource = dataSourceId == null
                ? null : context.metadataIndex().dataSource(dataSourceId);
        if (dataSourceId != null && (dataSource == null
                || !dataSource.metadata().enabled()
                || dataSource.metadata().connectionKind() != ConnectionKind.JDBC
                || !dataSource.metadata().purposes().contains(DataSourcePurpose.SOURCE)
                || !supportedDatabase(dataSource.metadata().jdbcDatabaseType()))) {
            issues.error(
                    "DATA_SOURCE_UNAVAILABLE",
                    "JDBC 增量输入需要已启用、具有 SOURCE 用途并支持增量读取的数据源",
                    "configuration.dataSourceId"
            );
        }
        MetadataTable table = dataSource == null ? null : dataSource.table(configuration.tableName());
        if (dataSource != null && table == null) {
            issues.error("TABLE_NOT_FOUND", "元数据快照中不存在物理表", "configuration.tableName");
        } else if (table != null) {
            if (table.objectType() != DatabaseObjectType.TABLE) {
                issues.error(
                        "JDBC_INCREMENTAL_TABLE_REQUIRED", "增量输入第一版只支持普通物理表",
                        "configuration.tableName");
            }
            if (table.columns().stream().anyMatch(column -> column.fieldType() == PlatformDataType.GEOMETRY)) {
                issues.error(
                        "JDBC_INCREMENTAL_GEOMETRY_UNSUPPORTED", "增量输入第一版不支持包含 Geometry 的表",
                        "configuration.tableName");
            }
            CanvasColumnSchema cursor = table.columns().stream()
                    .filter(column -> column.name().equals(configuration.incrementalTimeColumn()))
                    .findFirst().orElse(null);
            if (cursor == null) {
                issues.error(
                        "JDBC_INCREMENTAL_COLUMN_NOT_FOUND", "增量时间字段不存在",
                        "configuration.incrementalTimeColumn");
            } else if (cursor.nullable()
                    || cursor.fieldType() != PlatformDataType.TIMESTAMP
                    && cursor.fieldType() != PlatformDataType.TIMESTAMP_NTZ) {
                issues.error(
                        "JDBC_INCREMENTAL_COLUMN_UNSUPPORTED",
                        "增量时间字段必须是非空 TIMESTAMP 或 TIMESTAMP_NTZ",
                        "configuration.incrementalTimeColumn");
            }
            if (cursor != null && !table.indexedColumns().contains(cursor.name())) {
                issues.warning(
                        "JDBC_INCREMENTAL_COLUMN_NOT_INDEXED",
                        "增量时间字段没有索引，轮询可能产生全表扫描",
                        "configuration.incrementalTimeColumn"
                );
            }
        }
        if (issues.hasErrors()) return CanvasNodeOperationResult.invalid(List.of());
        CanvasTableSchema schema = new CanvasTableSchema(
                configuration.outputTableName(),
                CanvasTableOrigin.jdbcIncremental(dataSourceId, configuration.tableName()),
                table.columns(),
                CanvasDatasetKind.UNBOUNDED,
                null,
                null
        );
        Dataset<Row> dataset = context.dataAccess().readJdbcIncrementalInput(node, schema);
        return CanvasNodeOperationResult.propagated(
                Map.of(schema.name(), new SparkCanvasTable(schema, dataset)), List.of(schema));
    }

    private static boolean supportedDatabase(CanvasJdbcDatabaseType type) {
        return type == CanvasJdbcDatabaseType.POSTGRESQL
                || type == CanvasJdbcDatabaseType.HIGHGO
                || type == CanvasJdbcDatabaseType.MYSQL
                || type == CanvasJdbcDatabaseType.OPENGAUSS
                || type == CanvasJdbcDatabaseType.KINGBASE
                || type == CanvasJdbcDatabaseType.DAMENG
                || type == CanvasJdbcDatabaseType.ORACLE
                || type == CanvasJdbcDatabaseType.SQL_SERVER;
    }
}
