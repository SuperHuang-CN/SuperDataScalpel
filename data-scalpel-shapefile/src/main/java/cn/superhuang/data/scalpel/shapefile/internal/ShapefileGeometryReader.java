package cn.superhuang.data.scalpel.shapefile.internal;

import cn.superhuang.data.scalpel.shapefile.ShapefileErrorCode;
import cn.superhuang.data.scalpel.shapefile.ShapefileException;
import cn.superhuang.data.scalpel.shapefile.ShapefileReadLimits;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileEnvelope;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileShapeType;
import cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefileCoordinateSequence;
import cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefileGeometry;
import cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefileMultiPoint;
import cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefilePoint;
import cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefilePolygon;
import cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefilePolyline;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Decodes supported geometry records from SHP content. */
public final class ShapefileGeometryReader {
    private static final double M_NO_DATA_THRESHOLD = -1.0E38;

    private final RandomAccessObjectReader shp;
    private final ShapefileIndex index;
    private final ShapefileShapeType fileShapeType;
    private final ShapefileReadLimits limits;

    public ShapefileGeometryReader(
            RandomAccessObjectReader shp,
            ShapefileIndex index,
            ShapefileShapeType fileShapeType,
            ShapefileReadLimits limits) {
        this.shp = shp;
        this.index = index;
        this.fileShapeType = fileShapeType;
        this.limits = limits;
    }

    ShapefileGeometry read(long slot) {
        ShapefileIndex.Entry entry = index.entry(slot);
        ByteBuffer recordHeader = shp.read(entry.recordOffset(), 8, ByteOrder.BIG_ENDIAN);
        long declaredRecordNumber = Integer.toUnsignedLong(recordHeader.getInt());
        long expectedRecordNumber = slot + 1;
        if (declaredRecordNumber != expectedRecordNumber) {
            throw new ShapefileException(
                    ShapefileErrorCode.RECORD_MISMATCH,
                    "SHP record number does not match SHX slot " + expectedRecordNumber);
        }
        long headerLength = Integer.toUnsignedLong(recordHeader.getInt()) * 2L;
        if (headerLength != entry.contentLength()) {
            throw new ShapefileException(
                    ShapefileErrorCode.RECORD_MISMATCH,
                    "SHP record " + expectedRecordNumber + " length does not match SHX");
        }
        if (entry.contentLength() > limits.maxRecordBytes()) {
            throw new ShapefileException(
                    ShapefileErrorCode.LIMIT_EXCEEDED, "SHP record exceeds the configured byte limit");
        }
        int contentLength;
        try {
            contentLength = Math.toIntExact(entry.contentLength());
        } catch (ArithmeticException exception) {
            throw new ShapefileException(
                    ShapefileErrorCode.LIMIT_EXCEEDED, "SHP record cannot be materialized", exception);
        }
        ByteBuffer content = shp.read(entry.recordOffset() + 8, contentLength, ByteOrder.LITTLE_ENDIAN);
        BoundedRecordBuffer buffer = new BoundedRecordBuffer(content, expectedRecordNumber);
        int typeCode = buffer.readInt("shape type");
        if (typeCode == ShapefileShapeType.NULL.code()) {
            buffer.requireConsumed();
            return null;
        }
        ShapefileShapeType recordType = ShapefileShapeType.fromCode(typeCode);
        if (recordType != fileShapeType) {
            throw buffer.malformed("shape type does not match the file header");
        }
        ShapefileGeometry geometry = switch (recordType) {
            case POINT, POINT_Z, POINT_M -> readPoint(buffer, recordType);
            case MULTIPOINT, MULTIPOINT_Z, MULTIPOINT_M -> readMultiPoint(buffer, recordType);
            case POLYLINE, POLYLINE_Z, POLYLINE_M -> readMultipart(buffer, recordType, false);
            case POLYGON, POLYGON_Z, POLYGON_M -> readMultipart(buffer, recordType, true);
            case NULL, MULTIPATCH -> throw buffer.malformed("uses an unsupported shape type");
        };
        buffer.requireConsumed();
        return geometry;
    }

    private ShapefilePoint readPoint(BoundedRecordBuffer buffer, ShapefileShapeType type) {
        double x = buffer.readDouble("point X");
        double y = buffer.readDouble("point Y");
        Double z = type.hasZ() ? buffer.readDouble("point Z") : null;
        Double m = null;
        if (type == ShapefileShapeType.POINT_M) {
            m = normalizeM(buffer.readDouble("point M"));
        } else if (type == ShapefileShapeType.POINT_Z && buffer.remaining() > 0) {
            if (buffer.remaining() != 8) {
                throw buffer.malformed("contains an incomplete optional M value");
            }
            m = normalizeM(buffer.readDouble("point M"));
        }
        return new ShapefilePoint(x, y, z, m);
    }

    private ShapefileMultiPoint readMultiPoint(BoundedRecordBuffer buffer, ShapefileShapeType type) {
        double[] ranges = readXyEnvelope(buffer);
        int pointCount = count(buffer.readInt("point count"), limits.maxGeometryPoints(), "point count", buffer);
        buffer.require(bytesForValues(pointCount, 16, buffer, "XY values"), "XY values");
        double[] x = new double[pointCount];
        double[] y = new double[pointCount];
        readXy(buffer, x, y);
        DimensionValues values = readDimensions(buffer, type, pointCount);
        ShapefileEnvelope envelope = envelope(ranges, values);
        return new ShapefileMultiPoint(
                envelope, new ShapefileCoordinateSequence(x, y, values.z(), values.m()));
    }

    private ShapefileGeometry readMultipart(
            BoundedRecordBuffer buffer,
            ShapefileShapeType type,
            boolean polygon) {
        double[] ranges = readXyEnvelope(buffer);
        int partCount = count(buffer.readInt("part count"), limits.maxParts(), "part count", buffer);
        int pointCount = count(buffer.readInt("point count"), limits.maxGeometryPoints(), "point count", buffer);
        if (partCount <= 0 || pointCount <= 0 || partCount > pointCount) {
            throw buffer.malformed("has invalid part or point counts");
        }
        buffer.require(bytesForValues(partCount, 4, buffer, "part starts"), "part starts");
        int[] starts = new int[partCount];
        for (int index = 0; index < partCount; index++) {
            starts[index] = buffer.readInt("part start");
            if ((index == 0 && starts[index] != 0)
                    || (index > 0 && starts[index] <= starts[index - 1])
                    || starts[index] >= pointCount) {
                throw buffer.malformed("contains invalid part starts");
            }
        }
        int[] counts = new int[partCount];
        for (int index = 0; index < partCount; index++) {
            counts[index] = (index + 1 < partCount ? starts[index + 1] : pointCount) - starts[index];
            int minimum = polygon ? 4 : 2;
            if (counts[index] < minimum) {
                throw buffer.malformed("contains a part with too few points");
            }
        }
        buffer.require(bytesForValues(pointCount, 16, buffer, "XY values"), "XY values");
        double[] x = new double[pointCount];
        double[] y = new double[pointCount];
        readXy(buffer, x, y);
        if (polygon) {
            for (int part = 0; part < partCount; part++) {
                int first = starts[part];
                int last = first + counts[part] - 1;
                if (x[first] != x[last] || y[first] != y[last]) {
                    throw buffer.malformed("contains an unclosed polygon ring");
                }
            }
        }
        DimensionValues values = readDimensions(buffer, type, pointCount);
        ShapefileCoordinateSequence coordinates =
                new ShapefileCoordinateSequence(x, y, values.z(), values.m());
        ShapefileEnvelope envelope = envelope(ranges, values);
        return polygon
                ? new ShapefilePolygon(envelope, counts, coordinates)
                : new ShapefilePolyline(envelope, counts, coordinates);
    }

    private DimensionValues readDimensions(
            BoundedRecordBuffer buffer,
            ShapefileShapeType type,
            int pointCount) {
        double[] z = null;
        double zmin = Double.NaN;
        double zmax = Double.NaN;
        if (type.hasZ()) {
            buffer.require(dimensionBlockBytes(pointCount, buffer, "Z block"), "Z block");
            zmin = buffer.readDouble("Z minimum");
            zmax = buffer.readDouble("Z maximum");
            z = readArray(buffer, pointCount, false, "Z values");
        }
        double[] m = null;
        double mmin = Double.NaN;
        double mmax = Double.NaN;
        boolean requiredM = !type.hasZ() && type.hasM();
        if (requiredM || buffer.remaining() > 0) {
            long expected = dimensionBlockBytes(pointCount, buffer, "M block");
            if (buffer.remaining() != expected) {
                throw buffer.malformed("contains an incomplete M block");
            }
            mmin = normalizeM(buffer.readDouble("M minimum"));
            mmax = normalizeM(buffer.readDouble("M maximum"));
            m = readArray(buffer, pointCount, true, "M values");
        }
        return new DimensionValues(z, m, zmin, zmax, mmin, mmax);
    }

    private static double[] readXyEnvelope(BoundedRecordBuffer buffer) {
        return new double[] {
                buffer.readDouble("X minimum"), buffer.readDouble("Y minimum"),
                buffer.readDouble("X maximum"), buffer.readDouble("Y maximum")
        };
    }

    private static void readXy(BoundedRecordBuffer buffer, double[] x, double[] y) {
        for (int index = 0; index < x.length; index++) {
            x[index] = buffer.readDouble("point X");
            y[index] = buffer.readDouble("point Y");
        }
    }

    private static double[] readArray(
            BoundedRecordBuffer buffer,
            int length,
            boolean measure,
            String role) {
        double[] values = new double[length];
        for (int index = 0; index < length; index++) {
            double value = buffer.readDouble(role);
            values[index] = measure ? normalizeM(value) : value;
        }
        return values;
    }

    private static long dimensionBlockBytes(
            int pointCount,
            BoundedRecordBuffer buffer,
            String role) {
        return checkedAdd(16, bytesForValues(pointCount, 8, buffer, role), buffer, role);
    }

    private static long bytesForValues(
            int count,
            int bytesPerValue,
            BoundedRecordBuffer buffer,
            String role) {
        try {
            return Math.multiplyExact((long) count, bytesPerValue);
        } catch (ArithmeticException exception) {
            throw buffer.malformed(role + " byte length overflows");
        }
    }

    private static long checkedAdd(
            long left,
            long right,
            BoundedRecordBuffer buffer,
            String role) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw buffer.malformed(role + " byte length overflows");
        }
    }

    private static int count(int value, int maximum, String role, BoundedRecordBuffer buffer) {
        if (value < 0) {
            throw buffer.malformed("has a negative " + role);
        }
        if (value > maximum) {
            throw new ShapefileException(
                    ShapefileErrorCode.LIMIT_EXCEEDED, "SHP " + role + " exceeds the configured limit");
        }
        return value;
    }

    private static double normalizeM(double value) {
        return value < M_NO_DATA_THRESHOLD ? Double.NaN : value;
    }

    private static ShapefileEnvelope envelope(double[] xy, DimensionValues dimensions) {
        return new ShapefileEnvelope(
                xy[0], xy[1], xy[2], xy[3],
                dimensions.zmin(), dimensions.zmax(), dimensions.mmin(), dimensions.mmax());
    }

    private record DimensionValues(
            double[] z,
            double[] m,
            double zmin,
            double zmax,
            double mmin,
            double mmax) {
    }
}
