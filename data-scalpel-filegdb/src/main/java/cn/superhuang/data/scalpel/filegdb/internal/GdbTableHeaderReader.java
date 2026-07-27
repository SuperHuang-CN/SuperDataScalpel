package cn.superhuang.data.scalpel.filegdb.internal;

import cn.superhuang.data.scalpel.filegdb.FileGdbErrorCode;
import cn.superhuang.data.scalpel.filegdb.FileGdbException;
import cn.superhuang.data.scalpel.filegdb.FileGdbReadLimits;
import cn.superhuang.data.scalpel.filegdb.FileGdbSource;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbEnvelope;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbField;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbFieldType;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbLayerType;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbSpatialReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the schema block embedded in an uncompressed .gdbtable file. Field descriptor ordering was
 * independently re-expressed from the Apache-2.0 FileGDB-master reference listed in this module's
 * third-party notice.
 */
final class GdbTableHeaderReader {
    private static final int TABLE_SIGNATURE = 3;
    private static final int FIXED_HEADER_BYTES = 40;
    private static final long SCHEMA_OFFSET_POSITION = 32;

    private GdbTableHeaderReader() {
    }

    static GdbTableDefinition read(
            FileGdbSource source,
            String tableFileName,
            String physicalName,
            FileGdbReadLimits limits) {
        String role = physicalName + ".gdbtable";
        try (LittleEndianRandomAccessReader channel = LittleEndianRandomAccessReader.open(
                source,
                tableFileName,
                role,
                limits)) {
            if (channel.size() < FIXED_HEADER_BYTES) {
                throw new FileGdbException(FileGdbErrorCode.TRUNCATED_INPUT, role + " has no complete table header");
            }
            ByteBuffer fixed = channel.read(0, FIXED_HEADER_BYTES);
            int signature = fixed.getInt();
            if (signature != TABLE_SIGNATURE) {
                String detail = source.exists(tableFileName + ".cdf")
                        ? " uses FileGDB table compression"
                        : " has an unsupported table signature";
                throw new FileGdbException(FileGdbErrorCode.UNSUPPORTED_FORMAT, role + detail);
            }
            int declaredRecordCount = fixed.getInt();
            int largestRecordBytes = fixed.getInt();
            if (declaredRecordCount < 0 || largestRecordBytes < 0) {
                throw new FileGdbException(FileGdbErrorCode.MALFORMED_HEADER, role + " has negative header counts");
            }
            if (largestRecordBytes > limits.maxRecordBytes()) {
                throw new FileGdbException(
                        FileGdbErrorCode.LIMIT_EXCEEDED,
                        role + " declares a record larger than the configured limit");
            }
            long schemaOffset = channel.readLong(SCHEMA_OFFSET_POSITION);
            if (schemaOffset < FIXED_HEADER_BYTES || schemaOffset > channel.size() - Integer.BYTES) {
                throw new FileGdbException(FileGdbErrorCode.INVALID_OFFSET, role + " has an invalid schema offset");
            }
            int schemaLength = channel.readInt(schemaOffset);
            if (schemaLength <= 0) {
                throw new FileGdbException(FileGdbErrorCode.MALFORMED_HEADER, role + " has an invalid schema length");
            }
            if (schemaLength > limits.maxRecordBytes()) {
                throw new FileGdbException(FileGdbErrorCode.LIMIT_EXCEEDED, role + " schema exceeds the configured limit");
            }
            long schemaBodyOffset = checkedAdd(schemaOffset, Integer.BYTES, role + " schema offset overflow");
            ByteBuffer schemaBuffer = channel.read(schemaBodyOffset, schemaLength);
            return parseSchema(
                    tableFileName,
                    physicalName,
                    declaredRecordCount,
                    largestRecordBytes,
                    schemaBuffer,
                    limits);
        }
    }

    private static GdbTableDefinition parseSchema(
            String tableFileName,
            String physicalName,
            int declaredRecordCount,
            int largestRecordBytes,
            ByteBuffer schemaBuffer,
            FileGdbReadLimits limits) {
        String context = physicalName + " schema";
        BoundedBufferReader reader = new BoundedBufferReader(
                schemaBuffer,
                context,
                FileGdbErrorCode.MALFORMED_HEADER);
        int formatVersion = reader.readInt();
        if (formatVersion <= 0) {
            throw reader.malformed("has an invalid format version");
        }
        int geometryType = reader.readUnsignedByte();
        reader.readUnsignedByte();
        reader.readUnsignedByte();
        int geometryProperties = reader.readUnsignedByte();
        int fieldCount = reader.readUnsignedShort() & 0x7fff;
        if (fieldCount > limits.maxFields()) {
            throw new FileGdbException(FileGdbErrorCode.LIMIT_EXCEEDED, context + " has too many fields");
        }
        List<GdbFieldDefinition> fields = new ArrayList<>(fieldCount);
        FileGdbSpatialReference spatialReference = null;
        for (int index = 0; index < fieldCount; index++) {
            ParsedField parsed = parseField(reader, geometryType, geometryProperties, limits, index);
            fields.add(parsed.definition());
            if (parsed.spatialReference() != null) {
                if (spatialReference != null) {
                    throw reader.malformed("contains more than one shape field");
                }
                spatialReference = parsed.spatialReference();
            }
        }
        reader.requireFullyConsumed();
        FileGdbLayerType layerType = toLayerType(geometryType, spatialReference != null);
        if (layerType == FileGdbLayerType.TABLE && spatialReference != null) {
            throw reader.malformed("declares shape metadata without a geometry type");
        }
        return new GdbTableDefinition(
                physicalName,
                tableFileName,
                declaredRecordCount,
                largestRecordBytes,
                geometryType,
                geometryProperties,
                layerType,
                fields,
                spatialReference);
    }

    private static ParsedField parseField(
            BoundedBufferReader reader,
            int geometryType,
            int geometryProperties,
            FileGdbReadLimits limits,
            int fieldIndex) {
        int nameCharacters = reader.readUnsignedByte();
        String name = reader.readUtf16Characters(nameCharacters, limits.maxStringBytes());
        if (name.isEmpty()) {
            throw reader.malformed("field " + fieldIndex + " has an empty name");
        }
        int aliasCharacters = reader.readUnsignedByte();
        String alias = reader.readUtf16Characters(aliasCharacters, limits.maxStringBytes());
        if (alias.isEmpty()) {
            alias = name;
        }
        int typeCode = reader.readUnsignedByte();
        return switch (typeCode) {
            case 0 -> primitive(reader, name, alias, FileGdbFieldType.INT16, true);
            case 1 -> primitive(reader, name, alias, FileGdbFieldType.INT32, true);
            case 2 -> primitive(reader, name, alias, FileGdbFieldType.FLOAT32, true);
            case 3 -> primitive(reader, name, alias, FileGdbFieldType.FLOAT64, true);
            case 4 -> string(reader, name, alias);
            case 5 -> primitive(reader, name, alias, FileGdbFieldType.TIMESTAMP, true);
            case 6 -> primitive(reader, name, alias, FileGdbFieldType.OID, false);
            case 7 -> shape(reader, name, alias, geometryType, geometryProperties, limits);
            case 8 -> primitive(reader, name, alias, FileGdbFieldType.BINARY, false);
            case 9 -> throw new FileGdbException(
                    FileGdbErrorCode.UNSUPPORTED_FORMAT,
                    "Field " + name + " uses unsupported Raster storage");
            case 10 -> primitive(reader, name, alias, FileGdbFieldType.UUID, false);
            case 11 -> primitive(reader, name, alias, FileGdbFieldType.GUID, false);
            case 12 -> primitive(reader, name, alias, FileGdbFieldType.XML, false);
            default -> throw new FileGdbException(
                    FileGdbErrorCode.UNSUPPORTED_FORMAT,
                    "Field " + name + " has unsupported type code " + typeCode);
        };
    }

    private static ParsedField primitive(
            BoundedBufferReader reader,
            String name,
            String alias,
            FileGdbFieldType type,
            boolean hasDefaultValue) {
        int length = reader.readUnsignedByte();
        boolean nullable = (reader.readUnsignedByte() & 1) != 0;
        if (hasDefaultValue) {
            long defaultLength = reader.readVarUInt();
            if (defaultLength > Integer.MAX_VALUE) {
                throw new FileGdbException(FileGdbErrorCode.LIMIT_EXCEEDED, "Field " + name + " default is too large");
            }
            reader.skip((int) defaultLength);
        }
        FileGdbField field = new FileGdbField(name, alias, type, nullable, length);
        return new ParsedField(new GdbFieldDefinition(field, false), null);
    }

    private static ParsedField string(
            BoundedBufferReader reader,
            String name,
            String alias) {
        long length = Integer.toUnsignedLong(reader.readInt());
        boolean nullable = (reader.readUnsignedByte() & 1) != 0;
        long defaultLength = reader.readVarUInt();
        if (defaultLength > Integer.MAX_VALUE) {
            throw new FileGdbException(FileGdbErrorCode.LIMIT_EXCEEDED, "Field " + name + " default is too large");
        }
        reader.skip((int) defaultLength);
        FileGdbField field = new FileGdbField(name, alias, FileGdbFieldType.STRING, nullable, length);
        return new ParsedField(new GdbFieldDefinition(field, false), null);
    }

    private static ParsedField shape(
            BoundedBufferReader reader,
            String name,
            String alias,
            int geometryType,
            int geometryProperties,
            FileGdbReadLimits limits) {
        int length = reader.readUnsignedByte();
        boolean nullable = (reader.readUnsignedByte() & 1) != 0;
        int spatialReferenceBytes = reader.readUnsignedShort();
        if ((spatialReferenceBytes & 1) != 0) {
            throw reader.malformed("shape spatial-reference text has an odd byte length");
        }
        String wkt = reader.readUtf16Characters(spatialReferenceBytes / 2, limits.maxStringBytes());
        int coordinateFlags = reader.readUnsignedByte();
        boolean metadataHasZ = (coordinateFlags & 0x04) != 0;
        boolean metadataHasM = (coordinateFlags & 0x02) != 0;
        boolean hasZ = (geometryProperties & 0x80) != 0;
        boolean hasM = (geometryProperties & 0x40) != 0;
        if ((hasZ && !metadataHasZ) || (hasM && !metadataHasM)) {
            throw reader.malformed("shape Z/M metadata is missing for a declared dimension");
        }

        double xOrigin = finite(reader.readDouble(), reader, "x origin");
        double yOrigin = finite(reader.readDouble(), reader, "y origin");
        double xyScale = positiveFinite(reader.readDouble(), reader, "XY scale");
        Double metadataMOrigin = metadataHasM ? finite(reader.readDouble(), reader, "M origin") : null;
        Double metadataMScale = metadataHasM ? positiveFinite(reader.readDouble(), reader, "M scale") : null;
        Double metadataZOrigin = metadataHasZ ? finite(reader.readDouble(), reader, "Z origin") : null;
        Double metadataZScale = metadataHasZ ? positiveFinite(reader.readDouble(), reader, "Z scale") : null;
        double xyTolerance = nonNegativeFinite(reader.readDouble(), reader, "XY tolerance");
        Double metadataMTolerance = metadataHasM ? nonNegativeFinite(reader.readDouble(), reader, "M tolerance") : null;
        Double metadataZTolerance = metadataHasZ ? nonNegativeFinite(reader.readDouble(), reader, "Z tolerance") : null;

        double xMin = finite(reader.readDouble(), reader, "x minimum");
        double yMin = finite(reader.readDouble(), reader, "y minimum");
        double xMax = finite(reader.readDouble(), reader, "x maximum");
        double yMax = finite(reader.readDouble(), reader, "y maximum");
        Double zMin = hasZ ? dimensionExtent(reader.readDouble(), reader, "z minimum") : null;
        Double zMax = hasZ ? dimensionExtent(reader.readDouble(), reader, "z maximum") : null;
        Double mMin = hasM ? dimensionExtent(reader.readDouble(), reader, "m minimum") : null;
        Double mMax = hasM ? dimensionExtent(reader.readDouble(), reader, "m maximum") : null;
        reader.readUnsignedByte();
        int gridCount = reader.readInt();
        if (gridCount < 0 || gridCount > limits.maxFields()) {
            throw reader.malformed("shape grid count is invalid");
        }
        for (int index = 0; index < gridCount; index++) {
            positiveFinite(reader.readDouble(), reader, "grid size");
        }

        FileGdbSpatialReference spatialReference = new FileGdbSpatialReference(
                stripTrailingNull(wkt),
                hasZ,
                hasM,
                xOrigin,
                yOrigin,
                xyScale,
                hasZ ? metadataZOrigin : null,
                hasZ ? metadataZScale : null,
                hasM ? metadataMOrigin : null,
                hasM ? metadataMScale : null,
                xyTolerance,
                hasZ ? metadataZTolerance : null,
                hasM ? metadataMTolerance : null,
                new FileGdbEnvelope(xMin, yMin, xMax, yMax),
                zMin,
                zMax,
                mMin,
                mMax);
        FileGdbField field = new FileGdbField(name, alias, FileGdbFieldType.SHAPE, nullable, length);
        if (geometryType == 0) {
            throw reader.malformed("shape field has no declared geometry type");
        }
        return new ParsedField(new GdbFieldDefinition(field, true), spatialReference);
    }

    private static double finite(double value, BoundedBufferReader reader, String role) {
        if (!Double.isFinite(value)) {
            throw reader.malformed("shape " + role + " is not finite");
        }
        return value;
    }

    private static double positiveFinite(double value, BoundedBufferReader reader, String role) {
        finite(value, reader, role);
        if (value <= 0) {
            throw reader.malformed("shape " + role + " is not positive");
        }
        return value;
    }

    private static double dimensionExtent(double value, BoundedBufferReader reader, String role) {
        if (Double.isInfinite(value)) {
            throw reader.malformed("shape " + role + " is infinite");
        }
        return value;
    }

    private static double nonNegativeFinite(double value, BoundedBufferReader reader, String role) {
        finite(value, reader, role);
        if (value < 0) {
            throw reader.malformed("shape " + role + " is negative");
        }
        return value;
    }

    private static FileGdbLayerType toLayerType(int geometryType, boolean shapePresent) {
        if (!shapePresent && geometryType == 0) {
            return FileGdbLayerType.TABLE;
        }
        return switch (geometryType) {
            case 1 -> FileGdbLayerType.POINT;
            case 2 -> FileGdbLayerType.MULTIPOINT;
            case 3 -> FileGdbLayerType.POLYLINE;
            case 4, 5 -> FileGdbLayerType.POLYGON;
            case 9 -> FileGdbLayerType.MULTIPATCH;
            default -> FileGdbLayerType.UNKNOWN;
        };
    }

    private static String stripTrailingNull(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '\0') {
            end--;
        }
        return value.substring(0, end);
    }

    private static long checkedAdd(long left, long right, String message) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw new FileGdbException(FileGdbErrorCode.INVALID_OFFSET, message, exception);
        }
    }

    private record ParsedField(GdbFieldDefinition definition, FileGdbSpatialReference spatialReference) {
    }
}
