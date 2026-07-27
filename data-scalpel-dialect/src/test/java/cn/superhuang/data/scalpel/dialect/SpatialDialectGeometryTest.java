package cn.superhuang.data.scalpel.dialect;

import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.builtin.BuiltInDialects;
import cn.superhuang.data.scalpel.dialect.model.ColumnMetadata;
import cn.superhuang.data.scalpel.dialect.model.JdbcTypeDescriptor;
import cn.superhuang.data.scalpel.dialect.model.LogicalType;
import cn.superhuang.data.scalpel.dialect.model.PrimaryKeyMetadata;
import cn.superhuang.data.scalpel.dialect.model.SpatialColumnMetadata;
import cn.superhuang.data.scalpel.dialect.model.TableColumnDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableColumnType;
import cn.superhuang.data.scalpel.dialect.model.TableDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.model.TableMetadata;
import cn.superhuang.data.scalpel.dialect.model.TableStructureDifferenceType;
import cn.superhuang.data.scalpel.dialect.model.TableSummary;
import cn.superhuang.data.scalpel.dialect.model.TypeMappingQuality;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpatialDialectGeometryTest {

    private static final TableIdentifier POSTGRES_TABLE =
            new TableIdentifier("warehouse", "public", "spatial_asset");
    private static final TableIdentifier MYSQL_TABLE =
            new TableIdentifier("warehouse", null, "spatial_asset");

    private final DatabaseDialect postgres = BuiltInDialects.registry().require("POSTGRESQL");
    private final DatabaseDialect mysql = BuiltInDialects.registry().require("MYSQL");

    @Test
    void mapsAllGeometryKindsExactlyInBothDirections() {
        for (GeometryKind kind : GeometryKind.values()) {
            GeometryTypeDefinition geometry = geometry(kind, 4326, CoordinateDimension.XY);

            var postgresPhysical = postgres.mapToPhysicalType(PlatformTypeDefinition.geometry(geometry));
            var mysqlPhysical = mysql.mapToPhysicalType(PlatformTypeDefinition.geometry(geometry));
            assertEquals(TypeMappingQuality.EXACT, postgresPhysical.quality(), kind.name());
            assertEquals(TypeMappingQuality.EXACT, mysqlPhysical.quality(), kind.name());
            assertEquals(geometry, postgresPhysical.definition().geometry(), kind.name());
            assertEquals(geometry, mysqlPhysical.definition().geometry(), kind.name());

            assertEquals(
                    PlatformTypeDefinition.geometry(geometry),
                    postgres.mapToPlatformType(jdbcGeometry(
                            "geometry", kind.name(), 990001, "epsg", 4326,
                            CoordinateDimension.XY, true, true
                    )).definition(),
                    kind.name()
            );
            assertEquals(
                    PlatformTypeDefinition.geometry(geometry),
                    mysql.mapToPlatformType(jdbcGeometry(
                            kind.name(), kind.name(), 880001, "EPSG", 4326,
                            CoordinateDimension.XY, true, true
                    )).definition(),
                    kind.name()
            );
        }
    }

    @Test
    void rejectsGeometryWhenSubtypeCrsOrDimensionIsNotExact() {
        List<JdbcTypeDescriptor> postgresUnsupported = List.of(
                new JdbcTypeDescriptor(Types.OTHER, "geometry", null, null, null, null),
                jdbcGeometry("geometry", "POINT", null, "EPSG", 4326, CoordinateDimension.XY, true, false),
                jdbcGeometry("geometry", "POINT", 4326, "ESRI", 4326, CoordinateDimension.XY, true, true),
                jdbcGeometry("geometry", "POINTZ", 4326, "EPSG", 4326, CoordinateDimension.XYZ, true, true),
                jdbcGeometry("geometry", "POINTM", 4326, "EPSG", 4326, CoordinateDimension.XYM, true, true),
                jdbcGeometry("geometry", "POINTZM", 4326, "EPSG", 4326, CoordinateDimension.XYZM, true, true),
                new JdbcTypeDescriptor(Types.OTHER, "geography", null, null, null, null),
                new JdbcTypeDescriptor(Types.OTHER, "raster", null, null, null, null)
        );
        postgresUnsupported.forEach(type ->
                assertEquals(TypeMappingQuality.UNSUPPORTED, postgres.mapToPlatformType(type).quality()));

        List<JdbcTypeDescriptor> mysqlUnsupported = List.of(
                new JdbcTypeDescriptor(Types.OTHER, "POINT", null, null, null, null),
                jdbcGeometry("POINT", "POINT", null, "EPSG", 4326, CoordinateDimension.XY, true, false),
                jdbcGeometry("POINT", "POINT", 4326, "ESRI", 4326, CoordinateDimension.XY, true, true),
                jdbcGeometry("POINT", "POINT", 4326, "EPSG", 4326, CoordinateDimension.XYZ, true, true)
        );
        mysqlUnsupported.forEach(type ->
                assertEquals(TypeMappingQuality.UNSUPPORTED, mysql.mapToPlatformType(type).quality()));

        for (CoordinateDimension dimension : List.of(
                CoordinateDimension.XYZ, CoordinateDimension.XYM, CoordinateDimension.XYZM
        )) {
            PlatformTypeDefinition type = PlatformTypeDefinition.geometry(
                    geometry(GeometryKind.POINT, 4326, dimension)
            );
            assertEquals(TypeMappingQuality.UNSUPPORTED, postgres.mapToPhysicalType(type).quality());
            assertEquals(TypeMappingQuality.UNSUPPORTED, mysql.mapToPhysicalType(type).quality());
        }
        PlatformTypeDefinition nonEpsg = PlatformTypeDefinition.geometry(new GeometryTypeDefinition(
                GeometryKind.POINT, new CrsReference("ESRI", 4326), CoordinateDimension.XY
        ));
        assertEquals(TypeMappingQuality.UNSUPPORTED, postgres.mapToPhysicalType(nonEpsg).quality());
        assertEquals(TypeMappingQuality.UNSUPPORTED, mysql.mapToPhysicalType(nonEpsg).quality());
    }

    @Test
    void resolvesDatabaseLocalSpatialIdsWhenPlanningDdlWithoutCreatingIndexes() throws Exception {
        Connection postgis = connection("PostgreSQL", 16, sql -> {
            if (sql.contains("pg_extension")) {
                return List.of(Map.of("1", "postgis_runtime"));
            }
            if (sql.contains("spatial_ref_sys")) {
                return List.of(Map.of(
                        "srid", 990001,
                        "auth_name", "EPSG",
                        "auth_srid", 4326
                ));
            }
            return List.of();
        });
        String postgresSql = postgres.planCreateTable(postgis, definition(
                POSTGRES_TABLE, GeometryKind.POINT, 4326, true
        )).statements().getFirst();
        assertTrue(postgresSql.contains("\"postgis_runtime\".\"geometry\"(POINT,990001)"));
        assertNoSpatialIndex(postgresSql);

        Connection mysql8 = connection("MySQL", 8, sql -> {
            if (sql.contains("ST_SPATIAL_REFERENCE_SYSTEMS")) {
                return List.of(Map.of(
                        "SRS_ID", 880001,
                        "ORGANIZATION", "EPSG",
                        "ORGANIZATION_COORDSYS_ID", 4326
                ));
            }
            return List.of();
        });
        String mysqlSql = mysql.planCreateTable(mysql8, definition(
                MYSQL_TABLE, GeometryKind.MULTIPOLYGON, 4326, false
        )).statements().getFirst();
        assertTrue(mysqlSql.contains("`shape` MULTIPOLYGON SRID 880001 NOT NULL"));
        assertTrue(mysqlSql.endsWith(" ENGINE=InnoDB"));
        assertNoSpatialIndex(mysqlSql);
    }

    @Test
    void rejectsMissingRuntimeCapabilityUnknownCrsAndDuplicateCrsMappings() {
        Connection noPostgis = connection("PostgreSQL", 16, ignored -> List.of());
        IllegalArgumentException missingPostgis = assertThrows(
                IllegalArgumentException.class,
                () -> postgres.planCreateTable(
                        noPostgis, definition(POSTGRES_TABLE, GeometryKind.POINT, 4326, true)
                )
        );
        assertTrue(missingPostgis.getMessage().contains("PostGIS"));

        Function<String, List<Map<String, Object>>> unknownCrsRows = sql ->
                sql.contains("pg_extension") ? List.of(Map.of("1", "postgis")) : List.of();
        IllegalArgumentException unknownPostgisCrs = assertThrows(
                IllegalArgumentException.class,
                () -> postgres.planCreateTable(
                        connection("PostgreSQL", 16, unknownCrsRows),
                        definition(POSTGRES_TABLE, GeometryKind.POINT, 999999, true)
                )
        );
        assertTrue(unknownPostgisCrs.getMessage().contains("EPSG:999999"));

        IllegalArgumentException duplicateMySqlCrs = assertThrows(
                IllegalArgumentException.class,
                () -> mysql.planCreateTable(
                        connection("MySQL", 8, sql -> List.of(
                                Map.of("SRS_ID", 880001, "ORGANIZATION", "EPSG", "ORGANIZATION_COORDSYS_ID", 4326),
                                Map.of("SRS_ID", 880002, "ORGANIZATION", "EPSG", "ORGANIZATION_COORDSYS_ID", 4326)
                        )),
                        definition(MYSQL_TABLE, GeometryKind.POINT, 4326, true)
                )
        );
        assertTrue(duplicateMySqlCrs.getMessage().contains("多个 SRS ID"));

        for (Connection unsupported : List.of(
                connection("MySQL", 5, ignored -> List.of()),
                connection("MariaDB", 10, ignored -> List.of())
        )) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> mysql.planCreateTable(
                            unsupported, definition(MYSQL_TABLE, GeometryKind.POINT, 4326, true)
                    )
            );
            assertTrue(exception.getMessage().contains("MySQL 8.x"));
        }
    }

    @Test
    void comparesGeometryKindCrsDimensionAndNullabilityExactly() {
        TableDefinition expected = definition(POSTGRES_TABLE, GeometryKind.POINT, 4326, true);
        assertTrue(postgres.compareTable(expected, metadata(
                POSTGRES_TABLE,
                spatialColumn("POINT", 990001, "EPSG", 4326, CoordinateDimension.XY, true)
        )).compatible());

        for (ColumnMetadata drifted : List.of(
                spatialColumn("GEOMETRY", 990001, "EPSG", 4326, CoordinateDimension.XY, true),
                spatialColumn("LINESTRING", 990001, "EPSG", 4326, CoordinateDimension.XY, true),
                spatialColumn("POINT", 990001, "EPSG", 3857, CoordinateDimension.XY, true),
                spatialColumn("POINT", 990001, "EPSG", 4326, CoordinateDimension.XYZ, true)
        )) {
            var comparison = postgres.compareTable(expected, metadata(POSTGRES_TABLE, drifted));
            assertFalse(comparison.compatible());
            assertTrue(comparison.differences().stream()
                    .anyMatch(difference -> difference.type() == TableStructureDifferenceType.TYPE_MISMATCH));
        }

        var nullability = postgres.compareTable(expected, metadata(
                POSTGRES_TABLE,
                spatialColumn("POINT", 990001, "EPSG", 4326, CoordinateDimension.XY, false)
        ));
        assertFalse(nullability.compatible());
        assertTrue(nullability.differences().stream()
                .anyMatch(difference -> difference.type() == TableStructureDifferenceType.NULLABILITY_MISMATCH));
    }

    @Test
    void preservesScalarV2FingerprintAndVersionsGeometryFingerprintAttributes() {
        TableDefinition scalar = new TableDefinition(
                new TableIdentifier(null, "public", "scalar_table"),
                List.of(new TableColumnDefinition("id", TableColumnType.LONG, null, null, null, false)),
                List.of()
        );
        assertEquals(
                "3eac3e89081be7def38a25e3ba73d3744081c6fba6e025c8041a144ee3da43f9",
                scalar.structureFingerprint().value()
        );

        TableDefinition point = definition(POSTGRES_TABLE, GeometryKind.POINT, 4326, true);
        assertNotEquals(
                point.structureFingerprint(),
                definition(POSTGRES_TABLE, GeometryKind.LINESTRING, 4326, true).structureFingerprint()
        );
        assertNotEquals(
                point.structureFingerprint(),
                definition(POSTGRES_TABLE, GeometryKind.POINT, 3857, true).structureFingerprint()
        );
        assertNotEquals(
                point.structureFingerprint(),
                definition(POSTGRES_TABLE, GeometryKind.POINT, 4326, false).structureFingerprint()
        );
        TableDefinition xyz = new TableDefinition(
                POSTGRES_TABLE,
                List.of(new TableColumnDefinition(
                        "shape", TableColumnType.GEOMETRY, null, null, null, true, null,
                        geometry(GeometryKind.POINT, 4326, CoordinateDimension.XYZ)
                )),
                List.of()
        );
        assertNotEquals(point.structureFingerprint(), xyz.structureFingerprint());
    }

    private static TableDefinition definition(
            TableIdentifier table,
            GeometryKind kind,
            int epsg,
            boolean nullable
    ) {
        return new TableDefinition(
                table,
                List.of(new TableColumnDefinition(
                        "shape", TableColumnType.GEOMETRY, null, null, null, nullable, null,
                        geometry(kind, epsg, CoordinateDimension.XY)
                )),
                List.of()
        );
    }

    private static GeometryTypeDefinition geometry(
            GeometryKind kind,
            int epsg,
            CoordinateDimension dimension
    ) {
        return new GeometryTypeDefinition(kind, CrsReference.epsg(epsg), dimension);
    }

    private static JdbcTypeDescriptor jdbcGeometry(
            String nativeType,
            String nativeKind,
            Integer spatialReferenceId,
            String authority,
            Integer code,
            CoordinateDimension dimension,
            boolean subtypeConstrained,
            boolean crsConstrained
    ) {
        return new JdbcTypeDescriptor(
                Types.OTHER, nativeType, null, null, null, null,
                new SpatialColumnMetadata(
                        nativeKind, spatialReferenceId, authority, code, dimension,
                        subtypeConstrained, crsConstrained
                )
        );
    }

    private static ColumnMetadata spatialColumn(
            String nativeKind,
            Integer spatialReferenceId,
            String authority,
            Integer code,
            CoordinateDimension dimension,
            boolean nullable
    ) {
        return new ColumnMetadata(
                "shape", 1, Types.OTHER, "geometry", LogicalType.OTHER,
                null, null, null, nullable, null, false, false, null,
                new SpatialColumnMetadata(
                        nativeKind, spatialReferenceId, authority, code, dimension, true, true
                )
        );
    }

    private static TableMetadata metadata(TableIdentifier table, ColumnMetadata column) {
        return new TableMetadata(
                new TableSummary(table, "TABLE", null),
                List.of(column),
                new PrimaryKeyMetadata(null, List.of()),
                List.of()
        );
    }

    private static void assertNoSpatialIndex(String sql) {
        String upper = sql.toUpperCase(Locale.ROOT);
        assertFalse(upper.contains("SPATIAL INDEX"), sql);
        assertFalse(upper.contains("GIST"), sql);
        assertFalse(upper.contains("CREATE INDEX"), sql);
    }

    private static Connection connection(
            String productName,
            int majorVersion,
            Function<String, List<Map<String, Object>>> queryRows
    ) {
        DatabaseMetaData metadata = proxy(DatabaseMetaData.class, (proxy, method, arguments) -> switch (method.getName()) {
            case "getDatabaseProductName" -> productName;
            case "getDatabaseMajorVersion" -> majorVersion;
            default -> defaultValue(method.getReturnType());
        });
        return proxy(Connection.class, (proxy, method, arguments) -> switch (method.getName()) {
            case "getMetaData" -> metadata;
            case "createStatement" -> statement(queryRows);
            case "prepareStatement" -> preparedStatement((String) arguments[0], queryRows);
            case "close" -> null;
            case "isClosed" -> false;
            default -> defaultValue(method.getReturnType());
        });
    }

    private static Statement statement(Function<String, List<Map<String, Object>>> queryRows) {
        return proxy(Statement.class, (proxy, method, arguments) -> switch (method.getName()) {
            case "executeQuery" -> resultSet(queryRows.apply((String) arguments[0]));
            case "close" -> null;
            default -> defaultValue(method.getReturnType());
        });
    }

    private static PreparedStatement preparedStatement(
            String sql,
            Function<String, List<Map<String, Object>>> queryRows
    ) {
        return proxy(PreparedStatement.class, (proxy, method, arguments) -> switch (method.getName()) {
            case "executeQuery" -> resultSet(queryRows.apply(sql));
            case "setInt", "setString", "close" -> null;
            default -> defaultValue(method.getReturnType());
        });
    }

    private static ResultSet resultSet(List<Map<String, Object>> rows) {
        class Cursor {
            private int index = -1;
            private Object lastValue;
        }
        Cursor cursor = new Cursor();
        return proxy(ResultSet.class, (proxy, method, arguments) -> switch (method.getName()) {
            case "next" -> ++cursor.index < rows.size();
            case "getString" -> {
                Object value = value(rows, cursor.index, arguments[0]);
                cursor.lastValue = value;
                yield value == null ? null : String.valueOf(value);
            }
            case "getInt" -> {
                Object value = value(rows, cursor.index, arguments[0]);
                cursor.lastValue = value;
                yield value == null ? 0 : ((Number) value).intValue();
            }
            case "wasNull" -> cursor.lastValue == null;
            case "close" -> null;
            default -> defaultValue(method.getReturnType());
        });
    }

    private static Object value(
            List<Map<String, Object>> rows,
            int rowIndex,
            Object column
    ) {
        return rows.get(rowIndex).get(String.valueOf(column));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
    }
}
