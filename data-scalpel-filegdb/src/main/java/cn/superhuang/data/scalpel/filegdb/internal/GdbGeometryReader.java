package cn.superhuang.data.scalpel.filegdb.internal;

import cn.superhuang.data.scalpel.filegdb.FileGdbErrorCode;
import cn.superhuang.data.scalpel.filegdb.FileGdbException;
import cn.superhuang.data.scalpel.filegdb.FileGdbReadLimits;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbEnvelope;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbLayerType;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbSpatialReference;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbCoordinateSequence;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbGeometry;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbMultiPoint;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbPoint;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbPolygon;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbPolyline;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Decodes Point, MultiPoint, Polyline and Polygon geometry blobs. Quantization and delta-decoding
 * behavior was independently re-expressed from the Apache-2.0 FileGDB-master reference listed in
 * this module's third-party notice.
 */
final class GdbGeometryReader {
    private static final long CURVE_DESCRIPTION_FLAG = 0x2000_0000L;
    private static final int MISSING_M_ARRAY_MARKER = 0x42;

    private GdbGeometryReader() {
    }

    static FileGdbGeometry read(
            ByteBuffer blob,
            GdbTableDefinition definition,
            FileGdbReadLimits limits,
            int oid) {
        FileGdbLayerType layerType = definition.layerType();
        if (!layerType.isCursorReadable() || layerType == FileGdbLayerType.TABLE) {
            throw new FileGdbException(
                    FileGdbErrorCode.UNSUPPORTED_FORMAT,
                    definition.physicalName() + " uses unsupported geometry type " + layerType);
        }
        FileGdbSpatialReference spatialReference = definition.spatialReference();
        if (spatialReference == null) {
            throw new FileGdbException(
                    FileGdbErrorCode.MALFORMED_HEADER,
                    definition.physicalName() + " has no spatial metadata");
        }
        BoundedBufferReader reader = new BoundedBufferReader(
                blob,
                definition.physicalName() + " OID " + oid + " geometry",
                FileGdbErrorCode.MALFORMED_HEADER);
        try {
            long encodedType = reader.readVarUInt();
            if ((encodedType & CURVE_DESCRIPTION_FLAG) != 0) {
                throw new FileGdbException(
                        FileGdbErrorCode.UNSUPPORTED_FORMAT,
                        definition.physicalName() + " OID " + oid + " contains curve segments");
            }
            FileGdbGeometry geometry = switch (layerType) {
                case POINT -> readPoint(reader, spatialReference);
                case MULTIPOINT -> readMultiPoint(reader, spatialReference, limits);
                case POLYLINE -> readPolyline(reader, spatialReference, limits);
                case POLYGON -> readPolygon(reader, spatialReference, limits);
                default -> throw new FileGdbException(
                        FileGdbErrorCode.UNSUPPORTED_FORMAT,
                        definition.physicalName() + " uses unsupported geometry type " + layerType);
            };
            reader.requireFullyConsumed();
            return geometry;
        } catch (FileGdbException exception) {
            if (exception.code() == FileGdbErrorCode.TRUNCATED_INPUT) {
                throw new FileGdbException(
                        FileGdbErrorCode.MALFORMED_HEADER,
                        exception.getMessage(),
                        exception);
            }
            throw exception;
        }
    }

    private static FileGdbPoint readPoint(
            BoundedBufferReader reader,
            FileGdbSpatialReference spatialReference) {
        long encodedX = reader.readVarUInt();
        if (encodedX == 0) {
            return null;
        }
        long encodedY = reader.readVarUInt();
        double x = coordinate(encodedX - 1, spatialReference.xyScale(), spatialReference.xOrigin(), reader, "x");
        double y = coordinate(encodedY - 1, spatialReference.xyScale(), spatialReference.yOrigin(), reader, "y");
        Double z = spatialReference.hasZ()
                ? coordinate(
                        reader.readVarUInt() - 1,
                        spatialReference.zScale(),
                        spatialReference.zOrigin(),
                        reader,
                        "z")
                : null;
        Double m = spatialReference.hasM()
                ? coordinate(
                        reader.readVarUInt() - 1,
                        spatialReference.mScale(),
                        spatialReference.mOrigin(),
                        reader,
                        "m")
                : null;
        return new FileGdbPoint(x, y, z, m);
    }

    private static FileGdbMultiPoint readMultiPoint(
            BoundedBufferReader reader,
            FileGdbSpatialReference spatialReference,
            FileGdbReadLimits limits) {
        int pointCount = readPointCount(reader, limits);
        if (pointCount == 0) {
            return null;
        }
        FileGdbEnvelope envelope = readEnvelope(reader, spatialReference);
        FileGdbCoordinateSequence coordinates = readCoordinates(reader, spatialReference, pointCount);
        return new FileGdbMultiPoint(envelope, coordinates);
    }

    private static FileGdbPolyline readPolyline(
            BoundedBufferReader reader,
            FileGdbSpatialReference spatialReference,
            FileGdbReadLimits limits) {
        DecodedMultiPart decoded = readMultiPart(reader, spatialReference, limits);
        return decoded == null
                ? null
                : new FileGdbPolyline(decoded.envelope(), decoded.partPointCounts(), decoded.coordinates());
    }

    private static FileGdbPolygon readPolygon(
            BoundedBufferReader reader,
            FileGdbSpatialReference spatialReference,
            FileGdbReadLimits limits) {
        DecodedMultiPart decoded = readMultiPart(reader, spatialReference, limits);
        return decoded == null
                ? null
                : new FileGdbPolygon(decoded.envelope(), decoded.partPointCounts(), decoded.coordinates());
    }

    private static DecodedMultiPart readMultiPart(
            BoundedBufferReader reader,
            FileGdbSpatialReference spatialReference,
            FileGdbReadLimits limits) {
        int pointCount = readPointCount(reader, limits);
        if (pointCount == 0) {
            return null;
        }
        int partCount = reader.readVarUIntAsInt("geometry part count", limits.maxGeometryParts());
        if (partCount <= 0 || partCount > pointCount) {
            throw reader.malformed("has an invalid geometry part count");
        }

        FileGdbEnvelope envelope = readEnvelope(reader, spatialReference);
        int[] partPointCounts = readPartPointCounts(reader, pointCount, partCount);
        FileGdbCoordinateSequence coordinates = readCoordinates(reader, spatialReference, pointCount);
        return new DecodedMultiPart(envelope, partPointCounts, coordinates);
    }

    private static int readPointCount(
            BoundedBufferReader reader,
            FileGdbReadLimits limits) {
        return reader.readVarUIntAsInt("geometry point count", limits.maxGeometryPoints());
    }

    private static FileGdbEnvelope readEnvelope(
            BoundedBufferReader reader,
            FileGdbSpatialReference spatialReference) {
        double xMin = coordinate(
                reader.readVarUInt(), spatialReference.xyScale(), spatialReference.xOrigin(), reader, "xmin");
        double yMin = coordinate(
                reader.readVarUInt(), spatialReference.xyScale(), spatialReference.yOrigin(), reader, "ymin");
        double xMax = deltaCoordinate(
                reader.readVarUInt(), spatialReference.xyScale(), xMin, reader, "xmax");
        double yMax = deltaCoordinate(
                reader.readVarUInt(), spatialReference.xyScale(), yMin, reader, "ymax");
        return new FileGdbEnvelope(xMin, yMin, xMax, yMax);
    }

    private static int[] readPartPointCounts(
            BoundedBufferReader reader,
            int pointCount,
            int partCount) {
        int[] partPointCounts = new int[partCount];
        int assigned = 0;
        for (int part = 0; part < partCount - 1; part++) {
            int size = reader.readVarUIntAsInt("geometry part size", Integer.MAX_VALUE);
            if (size <= 0 || assigned > pointCount - size) {
                throw reader.malformed("has invalid geometry part sizes");
            }
            partPointCounts[part] = size;
            assigned += size;
        }
        partPointCounts[partCount - 1] = pointCount - assigned;
        if (partPointCounts[partCount - 1] <= 0) {
            throw reader.malformed("has an empty final geometry part");
        }
        return partPointCounts;
    }

    private static FileGdbCoordinateSequence readCoordinates(
            BoundedBufferReader reader,
            FileGdbSpatialReference spatialReference,
            int pointCount) {
        double[] x = new double[pointCount];
        double[] y = new double[pointCount];
        double[] z = spatialReference.hasZ() ? new double[pointCount] : null;
        double[] m = spatialReference.hasM() ? new double[pointCount] : null;

        long deltaX = 0;
        long deltaY = 0;
        for (int point = 0; point < pointCount; point++) {
            deltaX = checkedAdd(deltaX, reader.readVarInt(), reader, "x delta overflow");
            deltaY = checkedAdd(deltaY, reader.readVarInt(), reader, "y delta overflow");
            x[point] = coordinate(deltaX, spatialReference.xyScale(), spatialReference.xOrigin(), reader, "x");
            y[point] = coordinate(deltaY, spatialReference.xyScale(), spatialReference.yOrigin(), reader, "y");
        }
        if (z != null) {
            long deltaZ = 0;
            for (int point = 0; point < pointCount; point++) {
                deltaZ = checkedAdd(deltaZ, reader.readVarInt(), reader, "z delta overflow");
                z[point] = coordinate(deltaZ, spatialReference.zScale(), spatialReference.zOrigin(), reader, "z");
            }
        }
        if (m != null) {
            if (reader.remaining() > 0 && reader.peekUnsignedByte() == MISSING_M_ARRAY_MARKER) {
                reader.readUnsignedByte();
                Arrays.fill(m, Double.NaN);
            } else {
                long deltaM = 0;
                for (int point = 0; point < pointCount; point++) {
                    deltaM = checkedAdd(deltaM, reader.readVarInt(), reader, "m delta overflow");
                    m[point] = deltaM == -1
                            ? Double.NaN
                            : coordinate(deltaM, spatialReference.mScale(), spatialReference.mOrigin(), reader, "m");
                }
            }
        }
        return new FileGdbCoordinateSequence(x, y, z, m);
    }

    private static long checkedAdd(
            long left,
            long right,
            BoundedBufferReader reader,
            String detail) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw new FileGdbException(
                    FileGdbErrorCode.MALFORMED_HEADER,
                    reader.malformed(detail).getMessage(),
                    exception);
        }
    }

    private static double coordinate(
            long quantized,
            double scale,
            double origin,
            BoundedBufferReader reader,
            String role) {
        double value = quantized / scale + origin;
        if (!Double.isFinite(value)) {
            throw reader.malformed("produces a non-finite " + role + " coordinate");
        }
        return value;
    }

    private static double coordinate(
            long quantized,
            Double scale,
            Double origin,
            BoundedBufferReader reader,
            String role) {
        if (scale == null || origin == null) {
            throw reader.malformed("has no " + role + " quantization metadata");
        }
        return coordinate(quantized, scale.doubleValue(), origin.doubleValue(), reader, role);
    }

    private static double deltaCoordinate(
            long delta,
            double scale,
            double minimum,
            BoundedBufferReader reader,
            String role) {
        double value = delta / scale + minimum;
        if (!Double.isFinite(value)) {
            throw reader.malformed("produces a non-finite " + role + " envelope coordinate");
        }
        return value;
    }

    private record DecodedMultiPart(
            FileGdbEnvelope envelope,
            int[] partPointCounts,
            FileGdbCoordinateSequence coordinates) {
    }
}
