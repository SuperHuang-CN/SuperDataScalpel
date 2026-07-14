package cn.superhuang.data.scalpel.dialect.runtime;

import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.NamespaceMode;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.model.ColumnMetadata;
import cn.superhuang.data.scalpel.dialect.model.IndexMetadata;
import cn.superhuang.data.scalpel.dialect.model.NamespaceInfo;
import cn.superhuang.data.scalpel.dialect.model.PreviewColumn;
import cn.superhuang.data.scalpel.dialect.model.PrimaryKeyMetadata;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.model.TableList;
import cn.superhuang.data.scalpel.dialect.model.TableMetadata;
import cn.superhuang.data.scalpel.dialect.model.TablePreview;
import cn.superhuang.data.scalpel.dialect.model.TableQuery;
import cn.superhuang.data.scalpel.dialect.model.TableSummary;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

final class JdbcMetadataReader {

    private final DatabaseDialect dialect;

    JdbcMetadataReader(DatabaseDialect dialect) {
        this.dialect = dialect;
    }

    List<NamespaceInfo> listNamespaces(Connection connection, JdbcConnectionConfig config) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        NamespaceMode mode = dialect.definition().namespaceMode();
        String defaultCatalog = dialect.resolveCatalog(config, null);
        String defaultSchema = dialect.resolveSchema(config, null);
        Map<String, NamespaceInfo> namespaces = new LinkedHashMap<>();

        if (mode == NamespaceMode.CATALOG) {
            try (ResultSet resultSet = metadata.getCatalogs()) {
                while (resultSet.next()) {
                    String catalog = resultSet.getString("TABLE_CAT");
                    addNamespace(namespaces, catalog, null, defaultCatalog, defaultSchema);
                }
            }
        } else {
            try (ResultSet resultSet = metadata.getSchemas()) {
                while (resultSet.next()) {
                    String schema = resultSet.getString("TABLE_SCHEM");
                    String catalog = readOptional(resultSet, "TABLE_CATALOG");
                    addNamespace(namespaces, catalog, schema, defaultCatalog, defaultSchema);
                }
            }
        }

        if (namespaces.isEmpty()) {
            addNamespace(namespaces, defaultCatalog, defaultSchema, defaultCatalog, defaultSchema);
        }
        return namespaces.values().stream()
                .sorted(Comparator.comparing(NamespaceInfo::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    TableList listTables(Connection connection, JdbcConnectionConfig config, TableQuery query) throws SQLException {
        String catalog = dialect.resolveCatalog(config, query.catalog());
        String schema = dialect.resolveSchema(config, query.schema());
        String[] tableTypes = query.includeViews()
                ? new String[]{"TABLE", "VIEW", "MATERIALIZED VIEW"}
                : new String[]{"TABLE"};
        String normalizedKeyword = query.keyword() == null ? null : query.keyword().toLowerCase(Locale.ROOT);
        List<TableSummary> tables = new ArrayList<>();
        boolean truncated = false;

        try (ResultSet resultSet = connection.getMetaData().getTables(catalog, schema, "%", tableTypes)) {
            while (resultSet.next()) {
                String tableName = resultSet.getString("TABLE_NAME");
                if (normalizedKeyword != null && !tableName.toLowerCase(Locale.ROOT).contains(normalizedKeyword)) {
                    continue;
                }
                if (tables.size() == query.limit()) {
                    truncated = true;
                    break;
                }
                tables.add(new TableSummary(
                        new TableIdentifier(
                                resultSet.getString("TABLE_CAT"),
                                resultSet.getString("TABLE_SCHEM"),
                                tableName
                        ),
                        resultSet.getString("TABLE_TYPE"),
                        resultSet.getString("REMARKS")
                ));
            }
        }

        tables.sort(Comparator
                .comparing((TableSummary table) -> Objects.toString(table.identifier().schema(), ""), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(table -> table.identifier().table(), String.CASE_INSENSITIVE_ORDER));
        return new TableList(tables, truncated);
    }

    TableMetadata readTable(Connection connection, TableIdentifier table) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        TableSummary summary = readSummary(metadata, table);
        List<ColumnMetadata> columns = readColumns(metadata, table);
        if (columns.isEmpty()) {
            throw new DatabaseAccessException("TABLE_NOT_FOUND", "未找到指定的数据表", null);
        }
        if (summary == null) {
            summary = new TableSummary(table, "TABLE", null);
        }
        return new TableMetadata(
                summary,
                columns,
                readPrimaryKey(metadata, table),
                readIndexes(metadata, table)
        );
    }

    TablePreview preview(Connection connection, TableIdentifier table, int limit) throws SQLException {
        int fetchLimit = limit + 1;
        List<PreviewColumn> columns = new ArrayList<>();
        List<List<Object>> rows = new ArrayList<>();

        try (Statement statement = connection.createStatement()) {
            statement.setMaxRows(fetchLimit);
            try {
                statement.setQueryTimeout(15);
            } catch (SQLException ignored) {
                // Some drivers do not implement statement-level timeouts.
            }
            try (ResultSet resultSet = statement.executeQuery(dialect.previewSql(table, fetchLimit))) {
                ResultSetMetaData metadata = resultSet.getMetaData();
                for (int index = 1; index <= metadata.getColumnCount(); index++) {
                    columns.add(new PreviewColumn(
                            metadata.getColumnLabel(index),
                            metadata.getColumnTypeName(index),
                            dialect.logicalType(metadata.getColumnType(index), metadata.getColumnTypeName(index))
                    ));
                }
                while (resultSet.next() && rows.size() < fetchLimit) {
                    List<Object> row = new ArrayList<>(columns.size());
                    for (int index = 1; index <= columns.size(); index++) {
                        row.add(JdbcValueNormalizer.normalize(resultSet.getObject(index)));
                    }
                    rows.add(row);
                }
            }
        }

        boolean truncated = rows.size() > limit;
        if (truncated) {
            rows = new ArrayList<>(rows.subList(0, limit));
        }
        return new TablePreview(table, columns, rows, limit, truncated);
    }

    private TableSummary readSummary(DatabaseMetaData metadata, TableIdentifier table) throws SQLException {
        try (ResultSet resultSet = metadata.getTables(
                table.catalog(), table.schema(), table.table(),
                new String[]{"TABLE", "VIEW", "MATERIALIZED VIEW"}
        )) {
            while (resultSet.next()) {
                if (table.table().equals(resultSet.getString("TABLE_NAME"))) {
                    return new TableSummary(
                            new TableIdentifier(
                                    resultSet.getString("TABLE_CAT"),
                                    resultSet.getString("TABLE_SCHEM"),
                                    resultSet.getString("TABLE_NAME")
                            ),
                            resultSet.getString("TABLE_TYPE"),
                            resultSet.getString("REMARKS")
                    );
                }
            }
        }
        return null;
    }

    private List<ColumnMetadata> readColumns(DatabaseMetaData metadata, TableIdentifier table) throws SQLException {
        List<ColumnMetadata> columns = new ArrayList<>();
        try (ResultSet resultSet = metadata.getColumns(table.catalog(), table.schema(), table.table(), "%")) {
            while (resultSet.next()) {
                if (!table.table().equals(resultSet.getString("TABLE_NAME"))) {
                    continue;
                }
                int jdbcType = resultSet.getInt("DATA_TYPE");
                Integer columnSize = nullableInteger(resultSet, "COLUMN_SIZE");
                Integer scale = nullableInteger(resultSet, "DECIMAL_DIGITS");
                boolean numeric = isNumeric(jdbcType);
                columns.add(new ColumnMetadata(
                        resultSet.getString("COLUMN_NAME"),
                        resultSet.getInt("ORDINAL_POSITION"),
                        jdbcType,
                        resultSet.getString("TYPE_NAME"),
                        dialect.logicalType(jdbcType, resultSet.getString("TYPE_NAME")),
                        numeric ? null : columnSize,
                        numeric ? columnSize : null,
                        numeric ? scale : null,
                        resultSet.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls,
                        resultSet.getString("COLUMN_DEF"),
                        "YES".equalsIgnoreCase(readOptional(resultSet, "IS_AUTOINCREMENT")),
                        "YES".equalsIgnoreCase(readOptional(resultSet, "IS_GENERATEDCOLUMN")),
                        resultSet.getString("REMARKS")
                ));
            }
        }
        columns.sort(Comparator.comparingInt(ColumnMetadata::ordinal));
        return columns;
    }

    private PrimaryKeyMetadata readPrimaryKey(DatabaseMetaData metadata, TableIdentifier table) throws SQLException {
        record KeyColumn(short sequence, String name) {
        }
        List<KeyColumn> keyColumns = new ArrayList<>();
        String keyName = null;
        try (ResultSet resultSet = metadata.getPrimaryKeys(table.catalog(), table.schema(), table.table())) {
            while (resultSet.next()) {
                keyName = resultSet.getString("PK_NAME");
                keyColumns.add(new KeyColumn(resultSet.getShort("KEY_SEQ"), resultSet.getString("COLUMN_NAME")));
            }
        }
        if (keyColumns.isEmpty()) {
            return null;
        }
        keyColumns.sort(Comparator.comparingInt(KeyColumn::sequence));
        return new PrimaryKeyMetadata(keyName, keyColumns.stream().map(KeyColumn::name).toList());
    }

    private List<IndexMetadata> readIndexes(DatabaseMetaData metadata, TableIdentifier table) throws SQLException {
        final class IndexBuilder {
            private final String name;
            private final boolean unique;
            private final Map<Short, String> columns = new java.util.TreeMap<>();

            private IndexBuilder(String name, boolean unique) {
                this.name = name;
                this.unique = unique;
            }
        }

        Map<String, IndexBuilder> indexes = new LinkedHashMap<>();
        try (ResultSet resultSet = metadata.getIndexInfo(table.catalog(), table.schema(), table.table(), false, false)) {
            while (resultSet.next()) {
                if (resultSet.getShort("TYPE") == DatabaseMetaData.tableIndexStatistic) {
                    continue;
                }
                String name = resultSet.getString("INDEX_NAME");
                String column = resultSet.getString("COLUMN_NAME");
                if (name == null || column == null) {
                    continue;
                }
                IndexBuilder builder = indexes.computeIfAbsent(
                        name,
                        ignored -> new IndexBuilder(name, !resultSetBoolean(resultSet, "NON_UNIQUE"))
                );
                builder.columns.put(resultSet.getShort("ORDINAL_POSITION"), column);
            }
        }
        return indexes.values().stream()
                .map(index -> new IndexMetadata(index.name, index.unique, List.copyOf(index.columns.values())))
                .sorted(Comparator.comparing(IndexMetadata::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private void addNamespace(
            Map<String, NamespaceInfo> namespaces,
            String catalog,
            String schema,
            String defaultCatalog,
            String defaultSchema
    ) {
        if (catalog == null && schema == null) {
            return;
        }
        String displayName = catalog != null && schema != null ? catalog + " / " + schema : Objects.requireNonNullElse(schema, catalog);
        boolean isDefault = Objects.equals(catalog, defaultCatalog) && Objects.equals(schema, defaultSchema)
                || schema != null && Objects.equals(schema, defaultSchema);
        namespaces.putIfAbsent(Objects.toString(catalog, "") + "\u0000" + Objects.toString(schema, ""),
                new NamespaceInfo(catalog, schema, displayName, isDefault));
    }

    private static String readOptional(ResultSet resultSet, String column) {
        try {
            return resultSet.getString(column);
        } catch (SQLException exception) {
            return null;
        }
    }

    private static boolean resultSetBoolean(ResultSet resultSet, String column) {
        try {
            return resultSet.getBoolean(column);
        } catch (SQLException exception) {
            return false;
        }
    }

    private static Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private static boolean isNumeric(int jdbcType) {
        return jdbcType == Types.TINYINT || jdbcType == Types.SMALLINT || jdbcType == Types.INTEGER
                || jdbcType == Types.BIGINT || jdbcType == Types.NUMERIC || jdbcType == Types.DECIMAL
                || jdbcType == Types.FLOAT || jdbcType == Types.REAL || jdbcType == Types.DOUBLE;
    }
}
