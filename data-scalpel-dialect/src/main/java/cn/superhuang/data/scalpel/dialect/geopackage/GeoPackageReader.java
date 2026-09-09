package cn.superhuang.data.scalpel.dialect.geopackage;

import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Small, read-only GeoPackage access layer shared by file import and the Spark runner.
 *
 * <p>The implementation deliberately uses JDBC only: a GeoPackage is a SQLite file, and no
 * mutable SQLite or spatial runtime semantics are exposed outside this package.</p>
 */
public final class GeoPackageReader implements AutoCloseable {

    private static final byte[] SQLITE_HEADER = "SQLite format 3\000".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    private static final int DEFAULT_DECIMAL_PRECISION = 38;
    private static final int DEFAULT_DECIMAL_SCALE = 18;
    private static final java.util.regex.Pattern DECLARED_TYPE = java.util.regex.Pattern.compile(
            "^([A-Z ]+?)(?:\\s*\\(\\s*(\\d+)\\s*(?:,\\s*(\\d+)\\s*)?\\))?$"
    );

    private final Connection connection;

    private GeoPackageReader(Connection connection) {
        this.connection = connection;
    }

    public static GeoPackageReader open(Path path) {
        Objects.requireNonNull(path, "path");
        requireSqliteHeader(path);
        try {
            Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath().normalize().toUri() + "?mode=ro");
            try (var statement = connection.createStatement()) {
                statement.execute("PRAGMA query_only = ON");
            }
            GeoPackageReader reader = new GeoPackageReader(connection);
            reader.requireGeoPackageMetadata();
            return reader;
        } catch (SQLException exception) {
            throw failure("无法以只读方式打开 GeoPackage", exception);
        }
    }

    public List<DiscoveredTable> discoverTables() {
        String sql = "SELECT table_name, data_type FROM gpkg_contents "
                + "WHERE data_type IN ('features', 'attributes') ORDER BY table_name COLLATE BINARY";
        List<DiscoveredTable> tables = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                String tableName = requiredName(rows.getString(1), "gpkg_contents.table_name");
                String type = rows.getString(2);
                requireBusinessTable(tableName);
                tables.add(new DiscoveredTable(tableName, "features".equals(type)));
            }
        } catch (SQLException exception) {
            throw failure("无法读取 GeoPackage 图层目录", exception);
        }
        return List.copyOf(tables);
    }

    public TableSchema schema(String tableName) {
        String normalizedTable = requiredName(tableName, "GeoPackage 表名");
        DiscoveredTable table = discoverTables().stream()
                .filter(value -> value.tableName().equals(normalizedTable))
                .findFirst()
                .orElseThrow(() -> new GeoPackageException("GeoPackage 表不存在或不是可导入图层：" + normalizedTable));
        GeometryColumn geometry = table.features() ? geometryColumn(normalizedTable) : null;
        if (table.features() && geometry == null) {
            throw new GeoPackageException("GeoPackage features 图层缺少 gpkg_geometry_columns 元数据：" + normalizedTable);
        }
        List<Column> columns = tableColumns(normalizedTable, geometry);
        if (columns.isEmpty()) {
            throw new GeoPackageException("GeoPackage 表不包含字段：" + normalizedTable);
        }
        return new TableSchema(normalizedTable, table.features(), columns, geometry);
    }

    /**
     * Opens every physical column for validation and returns Geometry values to the caller when
     * requested. Use the overload below for attribute-only preview reads.
     */
    public RowCursor openRows(TableSchema schema, boolean includeGeometry) {
        return openRows(schema, true, includeGeometry);
    }

    /**
     * Opens one table cursor. Geometry can be selected for full binary validation without adding
     * it to the returned value map, so administration previews never expose or decode coordinates.
     */
    public RowCursor openRows(TableSchema schema, boolean validateGeometry, boolean includeGeometryValues) {
        Objects.requireNonNull(schema, "schema");
        if (includeGeometryValues && !validateGeometry) {
            throw new IllegalArgumentException("返回 Geometry 值前必须校验 Geometry");
        }
        List<Column> selectedColumns = schema.columns().stream()
                .filter(column -> validateGeometry || !column.geometry())
                .toList();
        String select = selectedColumns.stream()
                .map(Column::name)
                .map(GeoPackageReader::quoted)
                .reduce((left, right) -> left + ", " + right)
                .orElseThrow();
        String order = schema.primaryKeyColumn() == null ? "rowid" : quoted(schema.primaryKeyColumn());
        try {
            PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + select + " FROM " + quoted(schema.tableName()) + " ORDER BY " + order
            );
            return new RowCursor(statement, statement.executeQuery(), schema, selectedColumns, includeGeometryValues);
        } catch (SQLException exception) {
            throw failure("无法读取 GeoPackage 表 " + schema.tableName(), exception);
        }
    }

    private void requireGeoPackageMetadata() {
        requireTable("gpkg_contents");
        requireTable("gpkg_spatial_ref_sys");
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("PRAGMA application_id")) {
            if (!rows.next() || rows.getLong(1) != 0x47504B47L) {
                throw new GeoPackageException("SQLite 文件不是有效的 GeoPackage（application_id 无效）");
            }
        } catch (SQLException exception) {
            throw failure("无法校验 GeoPackage 标识", exception);
        }
    }

    private void requireTable(String tableName) {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?"
        )) {
            statement.setString(1, tableName);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new GeoPackageException("SQLite 文件缺少 GeoPackage 系统表 " + tableName);
                }
            }
        } catch (SQLException exception) {
            throw failure("无法校验 GeoPackage 系统表", exception);
        }
    }

    private GeometryColumn geometryColumn(String tableName) {
        // `gpkg_geometry_columns` is mandatory only when this package contains feature tables.
        // Attributes-only GeoPackages remain valid and must stay importable.
        requireTable("gpkg_geometry_columns");
        List<GeometryColumn> columns = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT column_name, geometry_type_name, srs_id, z, m FROM gpkg_geometry_columns WHERE table_name = ?"
        )) {
            statement.setString(1, tableName);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    columns.add(new GeometryColumn(
                            requiredName(rows.getString(1), "Geometry 字段名"),
                            geometryKind(rows.getString(2)),
                            rows.getInt(3), rows.getInt(4), rows.getInt(5), null
                    ));
                }
            }
        } catch (SQLException exception) {
            throw failure("无法读取 GeoPackage Geometry 元数据", exception);
        }
        if (columns.size() > 1) {
            throw new GeoPackageException("首版只支持一个 Geometry 字段：" + tableName);
        }
        if (columns.isEmpty()) {
            return null;
        }
        GeometryColumn column = columns.getFirst();
        if (column.z() == 1 || column.m() == 1) {
            throw new GeoPackageException("GeoPackage 图层声明 Geometry 必须包含 Z 或 M，当前只支持二维 XY：" + tableName);
        }
        if ((column.z() != 0 && column.z() != 2) || (column.m() != 0 && column.m() != 2)) {
            throw new GeoPackageException("GeoPackage Geometry Z/M 元数据无效：" + tableName);
        }
        return column.withCrs(crs(column.srsId(), tableName));
    }

    private CrsReference crs(int srsId, String tableName) {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT organization, organization_coordsys_id, definition FROM gpkg_spatial_ref_sys WHERE srs_id = ?"
        )) {
            statement.setInt(1, srsId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new GeoPackageException("GeoPackage 图层缺少空间参考定义：" + tableName);
                }
                String organization = rows.getString(1);
                int code = rows.getInt(2);
                String definition = rows.getString(3);
                if (!"EPSG".equalsIgnoreCase(organization) || code < 1) {
                    throw new GeoPackageException("GeoPackage 图层空间参考不是可识别的 EPSG：" + tableName);
                }
                String normalized = definition == null ? "" : definition.toLowerCase(Locale.ROOT);
                if (normalized.contains("dynamic") || normalized.contains("epoch") || normalized.contains("frameepoch")) {
                    throw new GeoPackageException("GeoPackage 图层使用带历元的动态 CRS，当前不支持：" + tableName);
                }
                return CrsReference.epsg(code);
            }
        } catch (SQLException exception) {
            throw failure("无法读取 GeoPackage 空间参考", exception);
        }
    }

    private List<Column> tableColumns(String tableName, GeometryColumn geometry) {
        List<Column> columns = new ArrayList<>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("PRAGMA table_info(" + quotedLiteral(tableName) + ")")) {
            int order = 0;
            boolean geometryFound = geometry == null;
            while (rows.next()) {
                String name = requiredName(rows.getString("name"), "GeoPackage 字段名");
                boolean primaryKey = rows.getInt("pk") > 0;
                boolean nullable = rows.getInt("notnull") == 0 && !primaryKey;
                if (geometry != null && geometry.columnName().equals(name)) {
                    geometryFound = true;
                    columns.add(new Column(name, order++, PlatformTypeDefinition.geometry(new GeometryTypeDefinition(
                            geometry.kind(), geometry.crs(), CoordinateDimension.XY
                    )), nullable, true, primaryKey));
                } else {
                    columns.add(new Column(name, order++, sqliteType(rows.getString("type"), name), nullable, false, primaryKey));
                }
            }
            if (!geometryFound) {
                throw new GeoPackageException("gpkg_geometry_columns 指向不存在的字段：" + geometry.columnName());
            }
        } catch (SQLException exception) {
            throw failure("无法读取 GeoPackage 表结构", exception);
        }
        return List.copyOf(columns);
    }

    private static PlatformTypeDefinition sqliteType(String declaration, String columnName) {
        String normalized = declaration == null ? "" : declaration.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
        java.util.regex.Matcher matcher = DECLARED_TYPE.matcher(normalized);
        if (!matcher.matches()) {
            throw new GeoPackageException("GeoPackage 字段 " + columnName + " 的声明类型不受支持：" + declaration);
        }
        String name = matcher.group(1).trim();
        Integer precisionOrLength = parseTypeParameter(matcher.group(2), columnName, declaration);
        Integer scale = parseTypeParameter(matcher.group(3), columnName, declaration);
        if (scale != null && precisionOrLength == null) {
            throw new GeoPackageException("GeoPackage 字段 " + columnName + " 的 DECIMAL scale 缺少 precision");
        }
        return switch (name) {
            case "BOOLEAN" -> scalarType(precisionOrLength, columnName, declaration, PlatformDataType.BOOLEAN);
            case "TINYINT", "SMALLINT", "MEDIUMINT", "INT", "INTEGER", "BIGINT", "INT2", "INT8" ->
                    scalarType(precisionOrLength, columnName, declaration, PlatformDataType.LONG);
            case "REAL", "FLOAT", "DOUBLE", "DOUBLE PRECISION" ->
                    scalarType(precisionOrLength, columnName, declaration, PlatformDataType.DOUBLE);
            case "NUMERIC", "DECIMAL" -> decimalType(precisionOrLength, scale, columnName);
            case "TEXT", "CLOB" -> rejectTypeParameters(precisionOrLength, columnName, declaration, PlatformTypeDefinition.string(null));
            case "VARCHAR", "CHARACTER", "NCHAR", "NVARCHAR", "CHAR" ->
                    PlatformTypeDefinition.string(precisionOrLength);
            case "BLOB" -> scalarType(precisionOrLength, columnName, declaration, PlatformDataType.BINARY);
            case "DATE" -> scalarType(precisionOrLength, columnName, declaration, PlatformDataType.DATE);
            case "DATETIME", "TIMESTAMP" -> scalarType(
                    precisionOrLength, columnName, declaration, PlatformDataType.TIMESTAMP
            );
            default -> throw new GeoPackageException("GeoPackage 字段 " + columnName + " 的声明类型不受支持：" + declaration);
        };
    }

    private static Integer parseTypeParameter(String value, String columnName, String declaration) {
        if (value == null) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 1 || parsed > Integer.MAX_VALUE) {
                throw new GeoPackageException("GeoPackage 字段 " + columnName + " 的类型参数无效：" + declaration);
            }
            return Math.toIntExact(parsed);
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new GeoPackageException("GeoPackage 字段 " + columnName + " 的类型参数无效：" + declaration, exception);
        }
    }

    private static PlatformTypeDefinition decimalType(Integer precision, Integer scale, String columnName) {
        int actualPrecision = precision == null ? DEFAULT_DECIMAL_PRECISION : precision;
        int actualScale = scale == null ? (precision == null ? DEFAULT_DECIMAL_SCALE : 0) : scale;
        if (actualPrecision > 38 || actualScale > actualPrecision) {
            throw new GeoPackageException("GeoPackage 字段 " + columnName + " 的 DECIMAL precision/scale 超出平台支持范围");
        }
        return PlatformTypeDefinition.decimal(actualPrecision, actualScale);
    }

    private static PlatformTypeDefinition rejectTypeParameters(
            Integer parameter,
            String columnName,
            String declaration,
            PlatformTypeDefinition type
    ) {
        if (parameter != null) {
            throw new GeoPackageException("GeoPackage 字段 " + columnName + " 的声明类型不支持长度参数：" + declaration);
        }
        return type;
    }

    private static PlatformTypeDefinition scalarType(
            Integer parameter,
            String columnName,
            String declaration,
            PlatformDataType type
    ) {
        return rejectTypeParameters(parameter, columnName, declaration, PlatformTypeDefinition.of(type));
    }

    private static void requireSqliteHeader(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            byte[] header = input.readNBytes(SQLITE_HEADER.length);
            if (!Arrays.equals(header, SQLITE_HEADER)) {
                throw new GeoPackageException("文件不是有效的 SQLite 数据库");
            }
        } catch (IOException exception) {
            throw failure("无法读取 GeoPackage 文件头", exception);
        }
    }

    private static void requireBusinessTable(String name) {
        if (name.startsWith("gpkg_") || name.startsWith("sqlite_")) {
            throw new GeoPackageException("gpkg_contents 包含非法业务表名：" + name);
        }
    }

    private static String requiredName(String value, String label) {
        if (value == null || value.isBlank() || value.indexOf('\u0000') >= 0) {
            throw new GeoPackageException(label + "无效");
        }
        return value.trim();
    }

    private static String quoted(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static String quotedLiteral(String value) {
        return '\'' + value.replace("'", "''") + '\'';
    }

    private static GeometryKind geometryKind(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "GEOMETRY" -> GeometryKind.GEOMETRY;
            case "POINT" -> GeometryKind.POINT;
            case "LINESTRING" -> GeometryKind.LINESTRING;
            case "POLYGON" -> GeometryKind.POLYGON;
            case "MULTIPOINT" -> GeometryKind.MULTIPOINT;
            case "MULTILINESTRING" -> GeometryKind.MULTILINESTRING;
            case "MULTIPOLYGON" -> GeometryKind.MULTIPOLYGON;
            case "GEOMETRYCOLLECTION" -> GeometryKind.GEOMETRYCOLLECTION;
            default -> throw new GeoPackageException("GeoPackage Geometry 类型不受支持：" + value);
        };
    }

    private static GeoPackageException failure(String message, Exception cause) {
        return new GeoPackageException(message, cause);
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException exception) {
            throw failure("关闭 GeoPackage 连接失败", exception);
        }
    }

    public record DiscoveredTable(String tableName, boolean features) {
    }

    public record TableSchema(String tableName, boolean features, List<Column> columns, GeometryColumn geometry) {
        public TableSchema {
            columns = List.copyOf(columns);
        }

        public String primaryKeyColumn() {
            return columns.stream().filter(Column::primaryKey).map(Column::name).findFirst().orElse(null);
        }
    }

    public record Column(
            String name,
            int sortOrder,
            PlatformTypeDefinition type,
            boolean nullable,
            boolean geometry,
            boolean primaryKey
    ) {
    }

    public record GeometryColumn(
            String columnName,
            GeometryKind kind,
            int srsId,
            int z,
            int m,
            CrsReference crs
    ) {
        private GeometryColumn withCrs(CrsReference value) {
            return new GeometryColumn(columnName, kind, srsId, z, m, value);
        }
    }

    public static final class RowCursor implements AutoCloseable {
        private final PreparedStatement statement;
        private final ResultSet rows;
        private final TableSchema schema;
        private final List<Column> selectedColumns;
        private final boolean includeGeometryValues;
        private long rowNumber;

        private RowCursor(
                PreparedStatement statement,
                ResultSet rows,
                TableSchema schema,
                List<Column> selectedColumns,
                boolean includeGeometryValues
        ) {
            this.statement = statement;
            this.rows = rows;
            this.schema = schema;
            this.selectedColumns = List.copyOf(selectedColumns);
            this.includeGeometryValues = includeGeometryValues;
        }

        public Map<String, Object> next() {
            try {
                if (!rows.next()) {
                    return null;
                }
                rowNumber++;
                Map<String, Object> values = new LinkedHashMap<>();
                for (int index = 0; index < selectedColumns.size(); index++) {
                    Column column = selectedColumns.get(index);
                    Object raw = rows.getObject(index + 1);
                    if (column.geometry()) {
                        byte[] geometry = raw == null ? null : requireBytes(raw, column.name());
                        GeoPackageGeometry decoded = geometry == null ? null : GeoPackageGeometry.decode(
                                geometry, schema.geometry().srsId(), schema.geometry().kind(),
                                schema.tableName(), column.name(), rowNumber
                        );
                        if (includeGeometryValues) {
                            values.put(column.name(), decoded == null ? null : decoded.wkb());
                        }
                    } else {
                        values.put(column.name(), value(raw, column, schema.tableName(), rowNumber));
                    }
                }
                return values;
            } catch (SQLException exception) {
                throw failure("读取 GeoPackage 第 " + (rowNumber + 1) + " 条记录失败", exception);
            }
        }

        private static Object value(Object raw, Column column, String table, long rowNumber) {
            if (raw == null) {
                if (!column.nullable()) {
                    throw new GeoPackageException("GeoPackage 表 " + table + " 第 " + rowNumber + " 条记录字段 " + column.name() + " 不允许为空");
                }
                return null;
            }
            try {
                return switch (column.type().type()) {
                    case BOOLEAN -> booleanValue(raw);
                    case LONG -> longValue(raw);
                    case DOUBLE -> doubleValue(raw);
                    case DECIMAL -> decimalValue(raw);
                    case STRING -> stringValue(raw);
                    case BINARY -> requireBytes(raw, column.name());
                    case DATE -> dateValue(raw);
                    case TIMESTAMP -> timestampValue(raw);
                    default -> throw invalidValue(table, rowNumber, column.name(), "值类型不匹配");
                };
            } catch (ArithmeticException | DateTimeParseException exception) {
                throw invalidValue(table, rowNumber, column.name(), "值格式无效");
            }
        }

        private static Boolean booleanValue(Object value) {
            if (value instanceof Boolean bool) return bool;
            if (value instanceof Number number && (number.longValue() == 0 || number.longValue() == 1)
                    && number.doubleValue() == number.longValue()) return number.longValue() == 1;
            throw new IllegalArgumentException();
        }

        private static Long longValue(Object value) {
            if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
                return ((Number) value).longValue();
            }
            throw new IllegalArgumentException();
        }

        private static Double doubleValue(Object value) {
            if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) throw new IllegalArgumentException();
            return number.doubleValue();
        }

        private static BigDecimal decimalValue(Object value) {
            BigDecimal decimal = value instanceof BigDecimal source ? source
                    : value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
                    ? BigDecimal.valueOf(((Number) value).longValue())
                    : value instanceof Float || value instanceof Double ? BigDecimal.valueOf(((Number) value).doubleValue())
                    : null;
            if (decimal == null || decimal.precision() > DEFAULT_DECIMAL_PRECISION || decimal.scale() > DEFAULT_DECIMAL_SCALE) {
                throw new IllegalArgumentException();
            }
            return decimal;
        }

        private static String stringValue(Object value) {
            if (value instanceof String text) return text;
            throw new IllegalArgumentException();
        }

        private static LocalDate dateValue(Object value) {
            return value instanceof LocalDate date ? date : LocalDate.parse(requireText(value));
        }

        private static Instant timestampValue(Object value) {
            if (value instanceof Instant instant) return instant;
            if (value instanceof OffsetDateTime timestamp) return timestamp.toInstant();
            if (value instanceof Timestamp timestamp) return timestamp.toInstant();
            return OffsetDateTime.parse(requireText(value)).toInstant();
        }

        private static String requireText(Object value) {
            if (!(value instanceof String text)) throw new IllegalArgumentException();
            return text;
        }

        private static byte[] requireBytes(Object value, String column) {
            if (value instanceof byte[] bytes) return bytes;
            throw new GeoPackageException("GeoPackage Geometry/BLOB 字段 " + column + " 不是二进制值");
        }

        private static GeoPackageException invalidValue(String table, long row, String column, String reason) {
            return new GeoPackageException("GeoPackage 表 " + table + " 第 " + row + " 条记录字段 " + column + reason);
        }

        @Override
        public void close() {
            try {
                rows.close();
            } catch (SQLException ignored) {
                // The statement close below retains the primary resource lifecycle.
            }
            try {
                statement.close();
            } catch (SQLException ignored) {
                // Cursor close is best effort.
            }
        }
    }
}
