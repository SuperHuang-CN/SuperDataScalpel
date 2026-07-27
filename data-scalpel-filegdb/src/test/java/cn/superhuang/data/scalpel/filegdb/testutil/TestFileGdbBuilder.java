package cn.superhuang.data.scalpel.filegdb.testutil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Creates small, deterministic, uncompressed FileGDB directories for parser tests. */
public final class TestFileGdbBuilder {
    private static final int OID = 6;
    private static final int SHAPE = 7;
    private static final int STRING = 4;
    private static final int INT16 = 0;
    private static final int INT32 = 1;
    private static final int FLOAT32 = 2;
    private static final int FLOAT64 = 3;
    private static final int TIMESTAMP = 5;
    private static final int BINARY = 8;
    private static final int UUID_TYPE = 10;
    private static final int GUID = 11;
    private static final int XML = 12;

    private static final double X_ORIGIN = -1_000;
    private static final double Y_ORIGIN = -1_000;
    private static final double Z_ORIGIN = -1_000;
    private static final double M_ORIGIN = -1_000;
    private static final double XY_SCALE = 1_000;
    private static final double Z_SCALE = 10;
    private static final double M_SCALE = 10;

    private TestFileGdbBuilder() {
    }

    public static Fixture scalarAndPoints(Path parent) throws IOException {
        Path directory = Files.createDirectory(parent.resolve("scalar-and-point.gdb"));
        List<TableSpec> tables = new ArrayList<>();

        List<FieldSpec> scalarFields = List.of(
                oid("OBJECTID", "Object ID"),
                shape(Dimensions.XY),
                field("Small", "Small", INT16, true, 2),
                field("Count", "Count", INT32, true, 4),
                field("Ratio", "Ratio", FLOAT32, true, 4),
                field("Measure", "Measure", FLOAT64, true, 8),
                string("Name", "显示名称", true, 255),
                field("ObservedAt", "Observed At", TIMESTAMP, true, 8),
                field("ExternalId", "External ID", UUID_TYPE, true, 16),
                field("GlobalId", "Global ID", GUID, true, 16),
                field("Payload", "Payload", BINARY, true, 0),
                field("Metadata", "Metadata", XML, true, 0),
                string("OptionalText", "Optional Text", true, 255),
                string("LongText", "Long Text", true, 255));
        Map<String, Object> complete = new LinkedHashMap<>();
        complete.put("Shape", point(Dimensions.XY, 12.25, -3.5, null, null));
        complete.put("Small", (short) -12);
        complete.put("Count", 42);
        complete.put("Ratio", 1.25f);
        complete.put("Measure", -99.5d);
        complete.put("Name", "中文 café");
        complete.put("ObservedAt", Instant.parse("2024-01-02T03:04:05Z"));
        complete.put("ExternalId", java.util.UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        complete.put("GlobalId", java.util.UUID.fromString("00112233-4455-6677-8899-aabbccddeeff"));
        complete.put("Payload", new byte[] {0, 1, (byte) 0xff});
        complete.put("Metadata", "<root>值</root>");
        complete.put("OptionalText", null);
        complete.put("LongText", "x".repeat(64));
        Map<String, Object> sparse = new LinkedHashMap<>();
        sparse.put("Shape", emptyGeometry(GeometryKind.POINT));
        sparse.put("Small", null);
        sparse.put("Count", -7);
        sparse.put("Ratio", null);
        sparse.put("Measure", null);
        sparse.put("Name", "");
        sparse.put("ObservedAt", Instant.EPOCH);
        sparse.put("ExternalId", null);
        sparse.put("GlobalId", null);
        sparse.put("Payload", new byte[0]);
        sparse.put("Metadata", "");
        sparse.put("OptionalText", null);
        sparse.put("LongText", null);
        tables.add(new TableSpec(
                9,
                "ScalarPointXY",
                GeometryKind.POINT,
                Dimensions.XY,
                scalarFields,
                List.of(new RowSpec(0, complete), new RowSpec(2, sparse)),
                4));

        tables.add(pointTable(10, "PointXYZ", Dimensions.XYZ, 5, 10, 20, 30d, null));
        tables.add(pointTable(11, "PointXYM", Dimensions.XYM, 6, -10, -20, null, 7.5));
        tables.add(pointTable(12, "PointXYZM", Dimensions.XYZM, 4, 1.5, 2.5, 3.5, 4.5));
        writeFixture(directory, tables);
        return fixture(directory, tables);
    }

    public static Fixture polygons(Path parent) throws IOException {
        Path directory = Files.createDirectory(parent.resolve("polygon.gdb"));
        List<TableSpec> tables = List.of(
                polygonTable(9, "PolygonXY", Dimensions.XY, 4, false),
                polygonTable(10, "PolygonXYZ", Dimensions.XYZ, 5, false),
                polygonTable(11, "PolygonXYM", Dimensions.XYM, 6, true),
                polygonTable(12, "PolygonXYZM", Dimensions.XYZM, 4, false));
        writeFixture(directory, tables);
        return fixture(directory, tables);
    }

    public static Fixture multiPointsAndPolylines(Path parent) throws IOException {
        Path directory = Files.createDirectory(parent.resolve("multipoint-and-polyline.gdb"));
        List<TableSpec> tables = List.of(
                multiPointTable(9, "MultiPointXY", Dimensions.XY, 4),
                multiPointTable(10, "MultiPointXYZ", Dimensions.XYZ, 5),
                multiPointTable(11, "MultiPointXYM", Dimensions.XYM, 6),
                multiPointTable(12, "MultiPointXYZM", Dimensions.XYZM, 4),
                polylineTable(13, "PolylineXY", Dimensions.XY, 5),
                polylineTable(14, "PolylineXYZ", Dimensions.XYZ, 6),
                polylineTable(15, "PolylineXYM", Dimensions.XYM, 4),
                polylineTable(16, "PolylineXYZM", Dimensions.XYZM, 5));
        writeFixture(directory, tables);
        return fixture(directory, tables);
    }

    private static Fixture fixture(Path directory, List<TableSpec> tables) {
        Map<String, String> ids = new LinkedHashMap<>();
        tables.forEach(table -> ids.put(table.logicalName(), physicalName(table.id())));
        return new Fixture(directory, Map.copyOf(ids));
    }

    private static TableSpec pointTable(
            int id,
            String name,
            Dimensions dimensions,
            int offsetWidth,
            double x,
            double y,
            Double z,
            Double m) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("Shape", point(dimensions, x, y, z, m));
        return new TableSpec(
                id,
                name,
                GeometryKind.POINT,
                dimensions,
                List.of(oid("OBJECTID", "OBJECTID"), shape(dimensions)),
                List.of(new RowSpec(0, values)),
                offsetWidth);
    }

    private static TableSpec polygonTable(
            int id,
            String name,
            Dimensions dimensions,
            int offsetWidth,
            boolean missingM) {
        double[] x = {0, 10, 10, 0, 0, 2, 2, 4, 4, 2};
        double[] y = {0, 0, 10, 10, 0, 2, 4, 4, 2, 2};
        double[] z = dimensions.hasZ ? new double[] {1, 2, 3, 4, 1, 5, 6, 7, 8, 5} : null;
        double[] m = dimensions.hasM
                ? (missingM
                        ? null
                        : new double[] {10, 20, 30, 40, 10, 50, 60, 70, 80, 50})
                : null;
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("Shape", polygon(dimensions, new int[] {5, 5}, x, y, z, m));
        values.put("Label", name + " feature");
        return new TableSpec(
                id,
                name,
                GeometryKind.POLYGON,
                dimensions,
                List.of(
                        oid("OBJECTID", "OBJECTID"),
                        shape(dimensions),
                        string("Label", "Label", true, 100)),
                List.of(new RowSpec(0, values)),
                offsetWidth);
    }

    private static TableSpec multiPointTable(
            int id,
            String name,
            Dimensions dimensions,
            int offsetWidth) {
        double[] x = {1, 5, -2, 9};
        double[] y = {2, -3, 4, 0};
        double[] z = dimensions.hasZ ? new double[] {10, 20, 30, 40} : null;
        double[] m = dimensions.hasM
                ? (dimensions.hasZ
                        ? new double[] {100, Double.NaN, 300, 400}
                        : new double[] {100, 200, 300, 400})
                : null;
        return geometryTable(
                id,
                name,
                GeometryKind.MULTIPOINT,
                dimensions,
                offsetWidth,
                multiPoint(dimensions, x, y, z, m),
                dimensions.hasM ? multiPoint(dimensions, x, y, z, null) : null);
    }

    private static TableSpec polylineTable(
            int id,
            String name,
            Dimensions dimensions,
            int offsetWidth) {
        int[] paths = {3, 2};
        double[] x = {0, 2, 4, 10, 12};
        double[] y = {0, 1, 0, 5, 7};
        double[] z = dimensions.hasZ ? new double[] {1, 2, 3, 4, 5} : null;
        double[] m = dimensions.hasM
                ? (dimensions.hasZ
                        ? new double[] {10, Double.NaN, 30, 40, 50}
                        : new double[] {10, 20, 30, 40, 50})
                : null;
        return geometryTable(
                id,
                name,
                GeometryKind.POLYLINE,
                dimensions,
                offsetWidth,
                multiPart(GeometryKind.POLYLINE, dimensions, paths, x, y, z, m),
                dimensions.hasM
                        ? multiPart(GeometryKind.POLYLINE, dimensions, paths, x, y, z, null)
                        : null);
    }

    private static TableSpec geometryTable(
            int id,
            String name,
            GeometryKind geometryKind,
            Dimensions dimensions,
            int offsetWidth,
            byte[] geometry,
            byte[] missingMGeometry) {
        List<RowSpec> rows = new ArrayList<>();
        rows.add(new RowSpec(0, geometryValues(geometry, name + " feature")));
        rows.add(new RowSpec(1, geometryValues(emptyGeometry(geometryKind), name + " empty")));
        if (missingMGeometry != null) {
            rows.add(new RowSpec(2, geometryValues(missingMGeometry, name + " missing M")));
        }
        return new TableSpec(
                id,
                name,
                geometryKind,
                dimensions,
                List.of(
                        oid("OBJECTID", "OBJECTID"),
                        shape(dimensions),
                        string("Label", "Label", true, 100)),
                List.copyOf(rows),
                offsetWidth);
    }

    private static Map<String, Object> geometryValues(byte[] geometry, String label) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("Shape", geometry);
        values.put("Label", label);
        return values;
    }

    private static void writeFixture(Path directory, List<TableSpec> tables) throws IOException {
        for (TableSpec table : tables) {
            writeTable(directory, table);
        }
        List<FieldSpec> catalogFields = List.of(
                oid("OBJECTID", "OBJECTID"),
                string("Name", "Name", false, 160),
                field("ID", "ID", INT32, false, 4));
        List<RowSpec> catalogRows = new ArrayList<>();
        Map<String, Object> systemCatalog = new LinkedHashMap<>();
        systemCatalog.put("Name", "GDB_SystemCatalog");
        systemCatalog.put("ID", 1);
        catalogRows.add(new RowSpec(0, systemCatalog));
        int slot = 1;
        for (TableSpec table : tables) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("Name", table.logicalName());
            values.put("ID", table.id());
            catalogRows.add(new RowSpec(slot++, values));
        }
        writeTable(directory, new TableSpec(
                1,
                "GDB_SystemCatalog",
                GeometryKind.TABLE,
                null,
                catalogFields,
                catalogRows,
                5));
        Files.write(directory.resolve("gdb"), new byte[] {0, 0, 0, 0});
    }

    private static void writeTable(Path directory, TableSpec table) throws IOException {
        byte[] schema = schema(table);
        List<byte[]> recordPayloads = table.rows().stream()
                .map(row -> record(table.fields(), row.values()))
                .toList();
        int largest = recordPayloads.stream().mapToInt(bytes -> bytes.length).max().orElse(0);
        int headerBytes = 40 + Integer.BYTES + schema.length;
        int total = headerBytes + recordPayloads.stream().mapToInt(bytes -> Integer.BYTES + bytes.length).sum();
        ByteBuffer tableBytes = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        tableBytes.putInt(3);
        tableBytes.putInt(recordPayloads.size());
        tableBytes.putInt(largest);
        tableBytes.putInt(5);
        tableBytes.position(24);
        tableBytes.putLong(total);
        tableBytes.putLong(40);
        tableBytes.putInt(schema.length);
        tableBytes.put(schema);

        Map<Integer, Long> offsets = new LinkedHashMap<>();
        for (int index = 0; index < table.rows().size(); index++) {
            RowSpec row = table.rows().get(index);
            byte[] payload = recordPayloads.get(index);
            offsets.put(row.slot(), (long) tableBytes.position());
            tableBytes.putInt(payload.length);
            tableBytes.put(payload);
        }
        Files.write(directory.resolve(physicalName(table.id()) + ".gdbtable"), tableBytes.array());
        writeIndex(directory, table.id(), table.offsetWidth(), offsets);
    }

    private static void writeIndex(
            Path directory,
            int tableId,
            int width,
            Map<Integer, Long> offsets) throws IOException {
        int highestSlot = offsets.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
        ByteBuffer index = ByteBuffer.allocate(16 + 1_024 * width).order(ByteOrder.LITTLE_ENDIAN);
        index.putInt(3);
        index.putInt(1);
        index.putInt(highestSlot + 1);
        index.putInt(width);
        for (int slot = 0; slot < 1_024; slot++) {
            long offset = offsets.getOrDefault(slot, 0L);
            for (int byteIndex = 0; byteIndex < width; byteIndex++) {
                index.put((byte) (offset >>> (byteIndex * 8)));
            }
        }
        Files.write(directory.resolve(physicalName(tableId) + ".gdbtablx"), index.array());
    }

    private static byte[] schema(TableSpec table) {
        Bytes bytes = new Bytes();
        bytes.int32(4);
        int geometryType = table.geometryKind().typeCode;
        bytes.u8(geometryType);
        bytes.u8(geometryType == 0 ? 1 : 3);
        bytes.u8(0);
        bytes.u8(table.dimensions() == null ? 0 : table.dimensions().properties);
        bytes.int16(table.fields().size());
        for (FieldSpec field : table.fields()) {
            bytes.utf16WithByteCharacterLength(field.name());
            bytes.utf16WithByteCharacterLength(field.alias());
            bytes.u8(field.type());
            if (field.type() == STRING) {
                bytes.int32(field.length());
                bytes.u8(field.nullable() ? 1 : 0);
                bytes.varUInt(0);
            } else if (field.type() == SHAPE) {
                geometryField(bytes, table.dimensions(), field.nullable());
            } else {
                bytes.u8(field.length());
                bytes.u8(field.nullable() ? 1 : 0);
                if (field.type() <= TIMESTAMP) {
                    bytes.varUInt(0);
                }
            }
        }
        return bytes.toByteArray();
    }

    private static void geometryField(Bytes bytes, Dimensions dimensions, boolean nullable) {
        bytes.u8(0);
        bytes.u8(nullable ? 1 : 0);
        byte[] wkt = "GEOGCS[\"WGS 84\"]".getBytes(StandardCharsets.UTF_16LE);
        bytes.int16(wkt.length);
        bytes.raw(wkt);
        bytes.u8(dimensions.coordinateFlags);
        bytes.float64(X_ORIGIN);
        bytes.float64(Y_ORIGIN);
        bytes.float64(XY_SCALE);
        if (dimensions.hasM) {
            bytes.float64(M_ORIGIN);
            bytes.float64(M_SCALE);
        }
        if (dimensions.hasZ) {
            bytes.float64(Z_ORIGIN);
            bytes.float64(Z_SCALE);
        }
        bytes.float64(0.001);
        if (dimensions.hasM) {
            bytes.float64(0.1);
        }
        if (dimensions.hasZ) {
            bytes.float64(0.1);
        }
        bytes.float64(-180);
        bytes.float64(-90);
        bytes.float64(180);
        bytes.float64(90);
        if (dimensions.hasZ) {
            bytes.float64(-1_000);
            bytes.float64(10_000);
        }
        if (dimensions.hasM) {
            bytes.float64(Double.NaN);
            bytes.float64(Double.NaN);
        }
        bytes.u8(0);
        bytes.int32(1);
        bytes.float64(1_000);
    }

    private static byte[] record(List<FieldSpec> fields, Map<String, Object> values) {
        Bytes bytes = new Bytes();
        int nullableCount = (int) fields.stream().filter(FieldSpec::nullable).count();
        byte[] nullBitmap = new byte[(nullableCount + 7) / 8];
        int nullableBit = 0;
        for (FieldSpec field : fields) {
            if (field.nullable()) {
                if (values.get(field.name()) == null) {
                    nullBitmap[nullableBit >>> 3] |= (byte) (1 << (nullableBit & 7));
                }
                nullableBit++;
            }
        }
        bytes.raw(nullBitmap);
        for (FieldSpec field : fields) {
            Object value = values.get(field.name());
            if (field.nullable() && value == null) {
                continue;
            }
            switch (field.type()) {
                case OID -> {
                }
                case INT16 -> bytes.int16((Short) value);
                case INT32 -> bytes.int32((Integer) value);
                case FLOAT32 -> bytes.float32((Float) value);
                case FLOAT64 -> bytes.float64((Double) value);
                case STRING, XML -> bytes.variableBytes(((String) value).getBytes(StandardCharsets.UTF_8));
                case TIMESTAMP -> {
                    Instant instant = (Instant) value;
                    bytes.float64(instant.toEpochMilli() / 86_400_000d + 25_569d);
                }
                case BINARY -> bytes.variableBytes((byte[]) value);
                case UUID_TYPE, GUID -> bytes.raw(fileGdbUuid((UUID) value));
                case SHAPE -> bytes.variableBytes((byte[]) value);
                default -> throw new IllegalArgumentException("Unsupported test field type " + field.type());
            }
        }
        return bytes.toByteArray();
    }

    private static byte[] point(
            Dimensions dimensions,
            double x,
            double y,
        Double z,
        Double m) {
        Bytes bytes = new Bytes();
        bytes.varUInt(GeometryKind.POINT.encodedType);
        bytes.varUInt(quantize(x, X_ORIGIN, XY_SCALE) + 1);
        bytes.varUInt(quantize(y, Y_ORIGIN, XY_SCALE) + 1);
        if (dimensions.hasZ) {
            bytes.varUInt(quantize(z, Z_ORIGIN, Z_SCALE) + 1);
        }
        if (dimensions.hasM) {
            bytes.varUInt(quantize(m, M_ORIGIN, M_SCALE) + 1);
        }
        return bytes.toByteArray();
    }

    private static byte[] emptyGeometry(GeometryKind geometryKind) {
        Bytes bytes = new Bytes();
        bytes.varUInt(geometryKind.encodedType);
        bytes.varUInt(0);
        return bytes.toByteArray();
    }

    private static byte[] multiPoint(
            Dimensions dimensions,
            double[] x,
            double[] y,
            double[] z,
            double[] m) {
        Bytes bytes = new Bytes();
        bytes.varUInt(GeometryKind.MULTIPOINT.encodedType);
        bytes.varUInt(x.length);
        writeEnvelope(bytes, x, y);
        writeXyDeltas(bytes, x, y);
        writeOptionalDimensions(bytes, dimensions, z, m);
        return bytes.toByteArray();
    }

    private static byte[] polygon(
            Dimensions dimensions,
            int[] parts,
            double[] x,
            double[] y,
            double[] z,
            double[] m) {
        return multiPart(GeometryKind.POLYGON, dimensions, parts, x, y, z, m);
    }

    private static byte[] multiPart(
            GeometryKind geometryKind,
            Dimensions dimensions,
            int[] parts,
            double[] x,
            double[] y,
            double[] z,
            double[] m) {
        Bytes bytes = new Bytes();
        bytes.varUInt(geometryKind.encodedType);
        bytes.varUInt(x.length);
        bytes.varUInt(parts.length);
        writeEnvelope(bytes, x, y);
        for (int index = 0; index < parts.length - 1; index++) {
            bytes.varUInt(parts[index]);
        }
        writeXyDeltas(bytes, x, y);
        writeOptionalDimensions(bytes, dimensions, z, m);
        return bytes.toByteArray();
    }

    private static void writeEnvelope(Bytes bytes, double[] x, double[] y) {
        double xMin = java.util.Arrays.stream(x).min().orElseThrow();
        double yMin = java.util.Arrays.stream(y).min().orElseThrow();
        double xMax = java.util.Arrays.stream(x).max().orElseThrow();
        double yMax = java.util.Arrays.stream(y).max().orElseThrow();
        bytes.varUInt(quantize(xMin, X_ORIGIN, XY_SCALE));
        bytes.varUInt(quantize(yMin, Y_ORIGIN, XY_SCALE));
        bytes.varUInt(Math.round((xMax - xMin) * XY_SCALE));
        bytes.varUInt(Math.round((yMax - yMin) * XY_SCALE));
    }

    private static void writeXyDeltas(Bytes bytes, double[] x, double[] y) {
        long previousX = 0;
        long previousY = 0;
        for (int index = 0; index < x.length; index++) {
            long currentX = quantize(x[index], X_ORIGIN, XY_SCALE);
            long currentY = quantize(y[index], Y_ORIGIN, XY_SCALE);
            bytes.varInt(currentX - previousX);
            bytes.varInt(currentY - previousY);
            previousX = currentX;
            previousY = currentY;
        }
    }

    private static void writeOptionalDimensions(
            Bytes bytes,
            Dimensions dimensions,
            double[] z,
            double[] m) {
        if (dimensions.hasZ) {
            writeDeltas(bytes, z, Z_ORIGIN, Z_SCALE);
        }
        if (dimensions.hasM) {
            if (m == null) {
                bytes.u8(0x42);
            } else {
                writeMDeltas(bytes, m);
            }
        }
    }

    private static void writeDeltas(Bytes bytes, double[] values, double origin, double scale) {
        long previous = 0;
        for (double value : values) {
            long current = quantize(value, origin, scale);
            bytes.varInt(current - previous);
            previous = current;
        }
    }

    private static void writeMDeltas(Bytes bytes, double[] values) {
        long previous = 0;
        for (double value : values) {
            long current = Double.isNaN(value) ? -1 : quantize(value, M_ORIGIN, M_SCALE);
            bytes.varInt(current - previous);
            previous = current;
        }
    }

    private static long quantize(Double value, double origin, double scale) {
        if (value == null) {
            throw new IllegalArgumentException("Missing test coordinate dimension");
        }
        return Math.round((value - origin) * scale);
    }

    private static byte[] fileGdbUuid(UUID uuid) {
        ByteBuffer standard = ByteBuffer.allocate(16);
        standard.putLong(uuid.getMostSignificantBits());
        standard.putLong(uuid.getLeastSignificantBits());
        byte[] source = standard.array();
        return new byte[] {
                source[3], source[2], source[1], source[0],
                source[5], source[4], source[7], source[6],
                source[8], source[9], source[10], source[11], source[12], source[13], source[14], source[15]
        };
    }

    private static FieldSpec oid(String name, String alias) {
        return field(name, alias, OID, false, 4);
    }

    private static FieldSpec shape(Dimensions dimensions) {
        return new FieldSpec("Shape", "Shape", SHAPE, true, 0, dimensions);
    }

    private static FieldSpec string(String name, String alias, boolean nullable, int length) {
        return field(name, alias, STRING, nullable, length);
    }

    private static FieldSpec field(String name, String alias, int type, boolean nullable, int length) {
        return new FieldSpec(name, alias, type, nullable, length, null);
    }

    private static String physicalName(int id) {
        return "a%08x".formatted(id);
    }

    public record Fixture(Path directory, Map<String, String> layerIds) {
        public String layerId(String logicalName) {
            String id = layerIds.get(logicalName);
            if (id == null) {
                throw new IllegalArgumentException("Unknown fixture layer " + logicalName);
            }
            return id;
        }
    }

    private enum GeometryKind {
        TABLE(0, 0),
        POINT(1, 1),
        MULTIPOINT(2, 8),
        POLYLINE(3, 3),
        POLYGON(4, 5);

        private final int typeCode;
        private final int encodedType;

        GeometryKind(int typeCode, int encodedType) {
            this.typeCode = typeCode;
            this.encodedType = encodedType;
        }
    }

    private enum Dimensions {
        XY(false, false, 0x00, 0x01),
        XYZ(true, false, 0x80, 0x05),
        XYM(false, true, 0x40, 0x03),
        XYZM(true, true, 0xc0, 0x07);

        private final boolean hasZ;
        private final boolean hasM;
        private final int properties;
        private final int coordinateFlags;

        Dimensions(boolean hasZ, boolean hasM, int properties, int coordinateFlags) {
            this.hasZ = hasZ;
            this.hasM = hasM;
            this.properties = properties;
            this.coordinateFlags = coordinateFlags;
        }
    }

    private record FieldSpec(
            String name,
            String alias,
            int type,
            boolean nullable,
            int length,
            Dimensions dimensions) {
    }

    private record RowSpec(int slot, Map<String, Object> values) {
    }

    private record TableSpec(
            int id,
            String logicalName,
            GeometryKind geometryKind,
            Dimensions dimensions,
            List<FieldSpec> fields,
            List<RowSpec> rows,
            int offsetWidth) {
    }

    private static final class Bytes {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        void u8(int value) {
            output.write(value & 0xff);
        }

        void int16(int value) {
            u8(value);
            u8(value >>> 8);
        }

        void int32(int value) {
            u8(value);
            u8(value >>> 8);
            u8(value >>> 16);
            u8(value >>> 24);
        }

        void float32(float value) {
            int32(Float.floatToRawIntBits(value));
        }

        void float64(double value) {
            long bits = Double.doubleToRawLongBits(value);
            for (int index = 0; index < Long.BYTES; index++) {
                u8((int) (bits >>> (index * 8)));
            }
        }

        void utf16WithByteCharacterLength(String value) {
            if (value.length() > 255) {
                throw new IllegalArgumentException("Test field name is too long");
            }
            u8(value.length());
            raw(value.getBytes(StandardCharsets.UTF_16LE));
        }

        void variableBytes(byte[] value) {
            varUInt(value.length);
            raw(value);
        }

        void varUInt(long value) {
            if (value < 0) {
                throw new IllegalArgumentException("Unsigned test value must not be negative");
            }
            do {
                int current = (int) (value & 0x7f);
                value >>>= 7;
                u8(value == 0 ? current : current | 0x80);
            } while (value != 0);
        }

        void varInt(long value) {
            boolean negative = value < 0;
            long magnitude = Math.abs(value);
            int first = (int) (magnitude & 0x3f);
            magnitude >>>= 6;
            if (negative) {
                first |= 0x40;
            }
            u8(magnitude == 0 ? first : first | 0x80);
            while (magnitude != 0) {
                int current = (int) (magnitude & 0x7f);
                magnitude >>>= 7;
                u8(magnitude == 0 ? current : current | 0x80);
            }
        }

        void raw(byte[] value) {
            output.writeBytes(value);
        }

        byte[] toByteArray() {
            return output.toByteArray();
        }
    }
}
