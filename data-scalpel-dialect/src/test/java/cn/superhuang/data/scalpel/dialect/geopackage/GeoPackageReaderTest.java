package cn.superhuang.data.scalpel.dialect.geopackage;

import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GeoPackageReaderTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversBusinessTablesMapsDeclaredTypesAndProjectsGeometryOutOfPreview() throws Exception {
        Path file = temporaryDirectory.resolve("sample.gpkg");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA application_id = 1196444487");
            statement.execute("CREATE TABLE gpkg_contents (table_name TEXT NOT NULL, data_type TEXT NOT NULL)");
            statement.execute("CREATE TABLE gpkg_spatial_ref_sys (srs_id INTEGER PRIMARY KEY, organization TEXT, organization_coordsys_id INTEGER, definition TEXT)");
            statement.execute("CREATE TABLE gpkg_geometry_columns (table_name TEXT, column_name TEXT, geometry_type_name TEXT, srs_id INTEGER, z INTEGER, m INTEGER)");
            statement.execute("CREATE TABLE notes (id INTEGER PRIMARY KEY, title VARCHAR(255) NOT NULL, observed_at DATETIME)");
            statement.execute("CREATE TABLE roads (id INTEGER PRIMARY KEY, name VARCHAR(255), shape BLOB)");
            statement.execute("INSERT INTO gpkg_contents VALUES ('roads', 'features'), ('notes', 'attributes')");
            statement.execute("INSERT INTO gpkg_spatial_ref_sys VALUES (4326, 'EPSG', 4326, 'GEOGCRS[\"WGS 84\"]')");
            statement.execute("INSERT INTO gpkg_geometry_columns VALUES ('roads', 'shape', 'POINT', 4326, 0, 0)");
            try (var insert = connection.prepareStatement("INSERT INTO roads VALUES (?, ?, ?)")) {
                insert.setLong(1, 1L);
                insert.setString(2, "主路");
                insert.setBytes(3, point(116.4, 39.9));
                insert.executeUpdate();
            }
        }

        try (GeoPackageReader reader = GeoPackageReader.open(file)) {
            assertEquals(
                    java.util.List.of("notes", "roads"),
                    reader.discoverTables().stream().map(GeoPackageReader.DiscoveredTable::tableName).toList()
            );
            assertEquals(PlatformDataType.TIMESTAMP, reader.schema("notes").columns().get(2).type().type());
            GeoPackageReader.TableSchema roads = reader.schema("roads");
            assertEquals(PlatformDataType.STRING, roads.columns().get(1).type().type());
            assertEquals(255, roads.columns().get(1).type().length());
            assertEquals(GeometryKind.POINT, roads.geometry().kind());
            assertEquals(4326, roads.geometry().crs().code());

            try (GeoPackageReader.RowCursor preview = reader.openRows(roads, false, false)) {
                Map<String, Object> row = preview.next();
                assertNotNull(row);
                assertEquals(1L, row.get("id"));
                assertEquals("主路", row.get("name"));
                assertFalse(row.containsKey("shape"));
            }
            try (GeoPackageReader.RowCursor validation = reader.openRows(roads, true, false)) {
                assertNotNull(validation.next());
            }
            try (GeoPackageReader.RowCursor geometry = reader.openGeometryRows(roads)) {
                Map<String, Object> row = geometry.next();
                assertEquals(java.util.Set.of("shape"), row.keySet());
                assertArrayEquals(java.util.Arrays.copyOfRange(point(116.4,39.9),8,29), (byte[])row.get("shape"));
                assertNull(geometry.next());
            }
        }

        // A corrupted attribute is deliberately outside a geometry projection, while corrupted
        // geometry must still fail. Import and task readers retain complete row validation.
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             Statement statement = connection.createStatement()) {
            statement.execute("UPDATE roads SET name=x'0102'");
        }
        try (GeoPackageReader reader = GeoPackageReader.open(file)) {
            var schema = reader.schema("roads");
            try (var geometry = reader.openGeometryRows(schema)) {
                assertNotNull(geometry.next().get("shape"));
            }
            try (var complete = reader.openRows(schema,true)) {
                assertThrows(IllegalArgumentException.class,complete::next);
            }
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             Statement statement = connection.createStatement()) {
            statement.execute("UPDATE roads SET shape=x'0102'");
        }
        try (GeoPackageReader reader = GeoPackageReader.open(file);
             var geometry = reader.openGeometryRows(reader.schema("roads"))) {
            assertThrows(GeoPackageException.class,geometry::next);
        }
    }

    @Test
    void readsTextWithOptionalCharacterLimitWrittenByGeoPackageTools() throws Exception {
        Path file = temporaryDirectory.resolve("bounded-text.gpkg");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA application_id = 1196444487");
            statement.execute("CREATE TABLE gpkg_contents (table_name TEXT NOT NULL, data_type TEXT NOT NULL)");
            statement.execute("CREATE TABLE gpkg_spatial_ref_sys (srs_id INTEGER PRIMARY KEY, organization TEXT, organization_coordsys_id INTEGER, definition TEXT)");
            statement.execute("CREATE TABLE stations (id INTEGER PRIMARY KEY, name TEXT(80), note TEXT)");
            statement.execute("INSERT INTO gpkg_contents VALUES ('stations', 'attributes')");
            statement.execute("INSERT INTO stations VALUES (1, '城市监测站', NULL)");
        }
        try (GeoPackageReader reader = GeoPackageReader.open(file)) {
            var schema = reader.schema("stations");
            assertEquals(PlatformDataType.STRING, schema.columns().get(1).type().type());
            assertEquals(80, schema.columns().get(1).type().length());
            assertNull(schema.columns().get(2).type().length());
            try (var rows = reader.openRows(schema, false)) {
                assertEquals("城市监测站", rows.next().get("name"));
                assertNull(rows.next());
            }
        }
    }

    @Test
    void acceptsAnAttributesOnlyGeoPackageWithoutGeometryMetadataTable() throws Exception {
        Path file = temporaryDirectory.resolve("attributes-only.gpkg");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA application_id = 1196444487");
            statement.execute("CREATE TABLE gpkg_contents (table_name TEXT NOT NULL, data_type TEXT NOT NULL)");
            statement.execute("CREATE TABLE gpkg_spatial_ref_sys (srs_id INTEGER PRIMARY KEY, organization TEXT, organization_coordsys_id INTEGER, definition TEXT)");
            statement.execute("CREATE TABLE codes (id INTEGER PRIMARY KEY, label TEXT)");
            statement.execute("INSERT INTO gpkg_contents VALUES ('codes', 'attributes')");
            statement.execute("INSERT INTO gpkg_spatial_ref_sys VALUES (4326, 'EPSG', 4326, 'GEOGCRS[\"WGS 84\"]')");
        }

        try (GeoPackageReader reader = GeoPackageReader.open(file)) {
            assertEquals(java.util.List.of("codes"), reader.discoverTables().stream()
                    .map(GeoPackageReader.DiscoveredTable::tableName).toList());
            assertEquals(2, reader.schema("codes").columns().size());
            assertThrows(IllegalArgumentException.class,() -> reader.openGeometryRows(reader.schema("codes")));
        }
    }

    private static byte[] point(double x, double y) {
        ByteBuffer value = ByteBuffer.allocate(8 + 1 + 4 + 16).order(ByteOrder.LITTLE_ENDIAN);
        value.put((byte) 'G').put((byte) 'P').put((byte) 0).put((byte) 1).putInt(4326);
        value.put((byte) 1).putInt(1).putDouble(x).putDouble(y);
        return value.array();
    }
}
