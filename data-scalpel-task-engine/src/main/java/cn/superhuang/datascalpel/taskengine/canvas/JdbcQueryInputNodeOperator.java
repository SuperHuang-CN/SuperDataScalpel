package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasJdbcDatabaseType;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableOrigin;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.ConnectionKind;
import cn.superhuang.data.scalpel.contract.task.DataSourcePurpose;
import cn.superhuang.data.scalpel.contract.task.JdbcQueryInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.JdbcQueryInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.dialect.query.InsertSelectQuery;
import cn.superhuang.data.scalpel.dialect.query.ReadOnlyQueryFingerprint;
import cn.superhuang.data.scalpel.dialect.query.ReadOnlySelectQueryParser;
import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A user-authored, explicitly analyzed read-only JDBC query that produces one bounded table. */
public final class JdbcQueryInputNodeOperator implements CanvasNodeOperator {

    private static final int MAX_SQL_LENGTH = 100_000;

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.JDBC_QUERY_INPUT;
    }

    @Override
    public CanvasNodeCategory category() {
        return CanvasNodeCategory.INPUT;
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
        if (!(definition instanceof JdbcQueryInputNodeDefinition node)) {
            throw new IllegalArgumentException("JDBC_QUERY_INPUT operator received " + definition.nodeType());
        }
        JdbcQueryInputConfiguration configuration = node.configuration();
        if (configuration == null) {
            return CanvasNodeOperationResult.invalid(List.of());
        }
        CanvasNodeIssueSink issues = context.issues();
        UUID dataSourceId = CanvasNodeSupport.parseUuid(
                configuration.dataSourceId(), "configuration.dataSourceId", issues);
        CanvasNodeSupport.required(
                configuration.sql(), "请输入只读 SQL", "configuration.sql", issues);
        CanvasNodeSupport.required(
                configuration.outputTableName(), "请输入输出逻辑表名", "configuration.outputTableName", issues);
        if (configuration.sql() != null && configuration.sql().length() > MAX_SQL_LENGTH) {
            issues.error(
                    "JDBC_QUERY_SQL_TOO_LONG",
                    "SQL 不能超过 100000 个字符",
                    "configuration.sql"
            );
        }
        if (configuration.outputColumns() == null || configuration.outputColumns().isEmpty()) {
            issues.error(
                    "JDBC_QUERY_SCHEMA_REQUIRED",
                    "请先分析 SQL 并保存查询结果字段",
                    "configuration.outputColumns"
            );
        }

        MetadataIndex.DataSourceEntry dataSource = dataSourceId == null
                ? null : context.metadataIndex().dataSource(dataSourceId);
        if (dataSourceId != null && (dataSource == null
                || !dataSource.metadata().enabled()
                || dataSource.metadata().connectionKind() != ConnectionKind.JDBC
                || !dataSource.metadata().purposes().contains(DataSourcePurpose.SOURCE))) {
            issues.error(
                    "DATA_SOURCE_UNAVAILABLE",
                    "查询输入数据源不存在、未启用或不具有 SOURCE 用途",
                    "configuration.dataSourceId"
            );
        } else if (dataSource != null
                && dataSource.metadata().jdbcDatabaseType() != CanvasJdbcDatabaseType.POSTGRESQL
                && dataSource.metadata().jdbcDatabaseType() != CanvasJdbcDatabaseType.HIGHGO
                && dataSource.metadata().jdbcDatabaseType() != CanvasJdbcDatabaseType.MYSQL
                && dataSource.metadata().jdbcDatabaseType() != CanvasJdbcDatabaseType.OPENGAUSS
                && dataSource.metadata().jdbcDatabaseType() != CanvasJdbcDatabaseType.KINGBASE) {
            issues.error(
                    "JDBC_QUERY_DATABASE_NOT_SUPPORTED",
                    "JDBC 查询输入只支持 PostgreSQL、HighGo、MySQL、openGauss 和人大金仓",
                    "configuration.dataSourceId"
            );
        }

        InsertSelectQuery query = parseQuery(configuration.sql(), issues);
        if (query != null) {
            String actualHash = ReadOnlyQueryFingerprint.sha256(query);
            if (!actualHash.equals(configuration.analyzedSqlSha256())) {
                issues.error(
                        "JDBC_QUERY_SCHEMA_STALE",
                        "SQL 已修改，请重新分析 SQL",
                        "configuration.analyzedSqlSha256"
                );
            }
        }
        validateColumns(configuration.outputColumns(), issues);
        if (issues.hasErrors()) {
            return CanvasNodeOperationResult.invalid(List.of());
        }

        CanvasTableSchema schema = new CanvasTableSchema(
                configuration.outputTableName(),
                CanvasTableOrigin.jdbcQuery(dataSourceId, configuration.outputTableName()),
                configuration.outputColumns()
        );
        Dataset<Row> dataset = context.dataAccess().readJdbcQueryInput(node, schema);
        return CanvasNodeOperationResult.propagated(
                Map.of(schema.name(), new SparkCanvasTable(schema, dataset)),
                List.of(schema)
        );
    }

    private static InsertSelectQuery parseQuery(String sql, CanvasNodeIssueSink issues) {
        if (CanvasNodeSupport.blank(sql) || sql.length() > MAX_SQL_LENGTH) {
            return null;
        }
        try {
            return ReadOnlySelectQueryParser.parse(sql);
        } catch (IllegalArgumentException exception) {
            issues.error(
                    "JDBC_QUERY_NOT_READ_ONLY",
                    "SQL 必须是单条只读 SELECT 或 WITH ... SELECT 查询",
                    "configuration.sql"
            );
            return null;
        }
    }

    private static void validateColumns(
            List<CanvasColumnSchema> columns,
            CanvasNodeIssueSink issues
    ) {
        if (columns == null) {
            return;
        }
        Set<String> names = new HashSet<>();
        for (int index = 0; index < columns.size(); index++) {
            CanvasColumnSchema column = columns.get(index);
            String path = "configuration.outputColumns[" + index + "]";
            if (column == null || CanvasNodeSupport.blank(column.name()) || column.fieldType() == null) {
                issues.error("JDBC_QUERY_SCHEMA_INVALID", "查询结果字段定义不完整", path);
                continue;
            }
            if (!names.add(column.name())) {
                issues.error(
                        "DUPLICATE_COLUMN_NAME",
                        "查询结果字段名重复：" + column.name(),
                        path + ".name"
                );
            }
            if (column.fieldType() == PlatformDataType.GEOMETRY) {
                issues.error(
                        "JDBC_QUERY_GEOMETRY_NOT_SUPPORTED",
                        "JDBC 查询输入第一版不支持 Geometry 字段",
                        path + ".fieldType"
                );
                continue;
            }
            if (column.fieldType() == PlatformDataType.STRING) {
                if (column.length() != null && column.length() < 1
                        || column.precision() != null || column.scale() != null) {
                    issues.error(
                            "JDBC_QUERY_SCHEMA_INVALID",
                            "STRING 字段 length 必须为空或为正数，且不能设置 precision/scale",
                            path
                    );
                }
                continue;
            }
            if (column.fieldType() == PlatformDataType.DECIMAL) {
                if (column.length() != null
                        || column.precision() == null || column.precision() < 1 || column.precision() > 38
                        || column.scale() == null || column.scale() < 0
                        || column.scale() > column.precision()) {
                    issues.error(
                            "JDBC_QUERY_SCHEMA_INVALID",
                            "DECIMAL precision 必须为 1 到 38，scale 必须为 0 到 precision，且不能设置 length",
                            path
                    );
                }
                continue;
            }
            if (column.length() != null || column.precision() != null || column.scale() != null) {
                issues.error(
                        "JDBC_QUERY_SCHEMA_INVALID",
                        column.fieldType() + " 字段不能设置 length、precision 或 scale",
                        path
                );
            }
        }
    }
}
