package cn.superhuang.data.scalpel.shapefile.testutil;

import cn.superhuang.data.scalpel.shapefile.model.ShapefileEnvelope;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileShapeType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Generates small deterministic component sets without using a GIS library. */
public final class TestShapefileBuilder {
    private final Path directory;
    private final String name;
    private final ShapefileShapeType shapeType;
    private final ShapefileEnvelope envelope;
    private final List<byte[]> geometries = new ArrayList<>();
    private final List<FieldSpec> fields = new ArrayList<>();
    private final List<RowSpec> rows = new ArrayList<>();
    private Charset dbfCharset = Charset.forName("UTF-8");
    private String cpg;
    private String prj;
    private int languageDriverId;

    public TestShapefileBuilder(
            Path directory,
            String name,
            ShapefileShapeType shapeType,
            ShapefileEnvelope envelope) {
        this.directory = directory;
        this.name = name;
        this.shapeType = shapeType;
        this.envelope = envelope;
    }

    public TestShapefileBuilder field(String name, char type, int length, int decimals) {
        fields.add(new FieldSpec(name, type, length, decimals));
        return this;
    }

    public TestShapefileBuilder record(byte[] geometry, boolean deleted, String... values) {
        geometries.add(geometry.clone());
        rows.add(new RowSpec(deleted, new ArrayList<>(Arrays.asList(values))));
        return this;
    }

    public TestShapefileBuilder dbfCharset(Charset charset) {
        this.dbfCharset = charset;
        return this;
    }

    public TestShapefileBuilder cpg(String cpg) {
        this.cpg = cpg;
        return this;
    }

    public TestShapefileBuilder languageDriverId(int languageDriverId) {
        this.languageDriverId = languageDriverId;
        return this;
    }

    public TestShapefileBuilder prj(String prj) {
        this.prj = prj;
        return this;
    }

    public Path write() throws IOException {
        if (geometries.size() != rows.size()) {
            throw new IllegalStateException("geometry and DBF record counts differ");
        }
        Files.createDirectories(directory);
        Path shp = directory.resolve(name + ".shp");
        Files.write(shp, shpBytes());
        Files.write(directory.resolve(name + ".shx"), shxBytes());
        Files.write(directory.resolve(name + ".dbf"), dbfBytes());
        if (cpg != null) {
            Files.writeString(directory.resolve(name + ".cpg"), cpg);
        }
        if (prj != null) {
            Files.writeString(directory.resolve(name + ".prj"), prj);
        }
        return shp;
    }

    private byte[] shpBytes() {
        int length = 100 + geometries.stream().mapToInt(value -> 8 + value.length).sum();
        ByteBuffer buffer = ByteBuffer.allocate(length);
        writeHeader(buffer, length);
        int recordNumber = 1;
        for (byte[] geometry : geometries) {
            buffer.order(ByteOrder.BIG_ENDIAN).putInt(recordNumber++).putInt(geometry.length / 2);
            buffer.put(geometry);
        }
        return buffer.array();
    }

    private byte[] shxBytes() {
        int length = 100 + geometries.size() * 8;
        ByteBuffer buffer = ByteBuffer.allocate(length);
        writeHeader(buffer, length);
        int offset = 100;
        for (byte[] geometry : geometries) {
            buffer.order(ByteOrder.BIG_ENDIAN).putInt(offset / 2).putInt(geometry.length / 2);
            offset += 8 + geometry.length;
        }
        return buffer.array();
    }

    private void writeHeader(ByteBuffer buffer, int length) {
        buffer.order(ByteOrder.BIG_ENDIAN).putInt(9994);
        for (int index = 0; index < 5; index++) {
            buffer.putInt(0);
        }
        buffer.putInt(length / 2);
        buffer.order(ByteOrder.LITTLE_ENDIAN).putInt(1000).putInt(shapeType.code());
        buffer.putDouble(envelope.xmin()).putDouble(envelope.ymin())
                .putDouble(envelope.xmax()).putDouble(envelope.ymax())
                .putDouble(envelope.zmin()).putDouble(envelope.zmax())
                .putDouble(envelope.mmin()).putDouble(envelope.mmax());
    }

    private byte[] dbfBytes() {
        int headerLength = 32 + fields.size() * 32 + 1;
        int recordLength = 1 + fields.stream().mapToInt(FieldSpec::length).sum();
        ByteBuffer buffer = ByteBuffer.allocate(headerLength + recordLength * rows.size() + 1)
                .order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) 0x03).put((byte) 124).put((byte) 1).put((byte) 1);
        buffer.putInt(rows.size()).putShort((short) headerLength).putShort((short) recordLength);
        buffer.position(29).put((byte) languageDriverId).position(32);
        for (FieldSpec field : fields) {
            byte[] nameBytes = field.name().getBytes(dbfCharset);
            if (nameBytes.length > 11) {
                throw new IllegalArgumentException("test DBF field name is too long");
            }
            buffer.put(Arrays.copyOf(nameBytes, 11));
            buffer.put((byte) field.type());
            buffer.putInt(0);
            buffer.put((byte) field.length()).put((byte) field.decimals());
            buffer.position(buffer.position() + 14);
        }
        buffer.put((byte) 0x0D);
        for (RowSpec row : rows) {
            if (row.values().size() != fields.size()) {
                throw new IllegalArgumentException("test DBF value count does not match fields");
            }
            buffer.put((byte) (row.deleted() ? 0x2A : 0x20));
            for (int index = 0; index < fields.size(); index++) {
                writeField(buffer, fields.get(index), row.values().get(index));
            }
        }
        buffer.put((byte) 0x1A);
        return buffer.array();
    }

    private void writeField(ByteBuffer buffer, FieldSpec field, String value) {
        byte[] target = new byte[field.length()];
        Arrays.fill(target, (byte) 0x20);
        if (value != null) {
            byte[] source = value.getBytes(field.type() == 'C' ? dbfCharset : Charset.forName("US-ASCII"));
            if (source.length > target.length) {
                throw new IllegalArgumentException("test DBF value is too long");
            }
            int offset = field.type() == 'C' ? 0 : target.length - source.length;
            System.arraycopy(source, 0, target, offset, source.length);
        }
        buffer.put(target);
    }

    public static byte[] nullShape() {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0).array();
    }

    public static byte[] point(ShapefileShapeType type, double x, double y, Double z, Double m) {
        int bytes = 4 + 16 + (type.hasZ() ? 8 : 0) + (m != null ? 8 : 0);
        ByteBuffer buffer = ByteBuffer.allocate(bytes).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(type.code()).putDouble(x).putDouble(y);
        if (type.hasZ()) {
            buffer.putDouble(z == null ? 0 : z);
        }
        if (m != null) {
            buffer.putDouble(m);
        }
        return buffer.array();
    }

    public static byte[] multiPoint(
            ShapefileShapeType type,
            double[] x,
            double[] y,
            double[] z,
            double[] m) {
        requireCoordinates(x, y, z, m);
        int bytes = 4 + 32 + 4 + 16 * x.length;
        if (z != null) bytes += 16 + 8 * x.length;
        if (m != null) bytes += 16 + 8 * x.length;
        ByteBuffer buffer = ByteBuffer.allocate(bytes).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(type.code());
        putXyEnvelope(buffer, x, y);
        buffer.putInt(x.length);
        putXy(buffer, x, y);
        putDimension(buffer, z);
        putDimension(buffer, m);
        return buffer.array();
    }

    public static byte[] multipart(
            ShapefileShapeType type,
            int[] partPointCounts,
            double[] x,
            double[] y,
            double[] z,
            double[] m) {
        requireCoordinates(x, y, z, m);
        int bytes = 4 + 32 + 8 + 4 * partPointCounts.length + 16 * x.length;
        if (z != null) bytes += 16 + 8 * x.length;
        if (m != null) bytes += 16 + 8 * x.length;
        ByteBuffer buffer = ByteBuffer.allocate(bytes).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(type.code());
        putXyEnvelope(buffer, x, y);
        buffer.putInt(partPointCounts.length).putInt(x.length);
        int start = 0;
        for (int count : partPointCounts) {
            buffer.putInt(start);
            start += count;
        }
        if (start != x.length) {
            throw new IllegalArgumentException("part counts do not cover coordinates");
        }
        putXy(buffer, x, y);
        putDimension(buffer, z);
        putDimension(buffer, m);
        return buffer.array();
    }

    private static void putXyEnvelope(ByteBuffer buffer, double[] x, double[] y) {
        buffer.putDouble(Arrays.stream(x).min().orElse(0))
                .putDouble(Arrays.stream(y).min().orElse(0))
                .putDouble(Arrays.stream(x).max().orElse(0))
                .putDouble(Arrays.stream(y).max().orElse(0));
    }

    private static void putXy(ByteBuffer buffer, double[] x, double[] y) {
        for (int index = 0; index < x.length; index++) {
            buffer.putDouble(x[index]).putDouble(y[index]);
        }
    }

    private static void putDimension(ByteBuffer buffer, double[] values) {
        if (values == null) {
            return;
        }
        buffer.putDouble(Arrays.stream(values).min().orElse(0))
                .putDouble(Arrays.stream(values).max().orElse(0));
        for (double value : values) {
            buffer.putDouble(value);
        }
    }

    private static void requireCoordinates(double[] x, double[] y, double[] z, double[] m) {
        if (x.length != y.length || (z != null && z.length != x.length) || (m != null && m.length != x.length)) {
            throw new IllegalArgumentException("coordinate dimensions differ");
        }
    }

    public record FieldSpec(String name, char type, int length, int decimals) {
    }

    private record RowSpec(boolean deleted, List<String> values) {
    }
}
