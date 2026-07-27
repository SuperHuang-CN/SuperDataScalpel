package cn.superhuang.data.scalpel.filegdb.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.superhuang.data.scalpel.filegdb.FileGdbErrorCode;
import cn.superhuang.data.scalpel.filegdb.FileGdbException;
import cn.superhuang.data.scalpel.filegdb.FileGdbReadLimits;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbEnvelope;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbLayerType;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbSpatialReference;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;

class GdbGeometryReaderMalformedTest {
    private static final long CURVE_DESCRIPTION_FLAG = 0x2000_0000L;

    @Test
    void appliesPointAndPartLimitsBeforeAllocatingArrays() {
        Bytes tooManyPoints = new Bytes();
        tooManyPoints.varUInt(8);
        tooManyPoints.varUInt(3);
        assertCode(
                FileGdbErrorCode.LIMIT_EXCEEDED,
                tooManyPoints,
                definition(FileGdbLayerType.MULTIPOINT, false, false),
                limits(10, 2));

        Bytes tooManyParts = new Bytes();
        tooManyParts.varUInt(3);
        tooManyParts.varUInt(2);
        tooManyParts.varUInt(2);
        assertCode(
                FileGdbErrorCode.LIMIT_EXCEEDED,
                tooManyParts,
                definition(FileGdbLayerType.POLYLINE, false, false),
                limits(1, 10));
    }

    @Test
    void rejectsInvalidPolylinePartStructures() {
        assertMalformed(multiPartPrefix(1, 0));
        assertMalformed(multiPartPrefix(1, 2));
        assertMalformed(multiPartPrefix(3, 2, 0));
        assertMalformed(multiPartPrefix(3, 2, 4));
        assertMalformed(multiPartPrefix(3, 2, 3));
    }

    @Test
    void rejectsTruncationOverflowTrailingBytesAndCurves() {
        Bytes truncatedEnvelope = new Bytes();
        truncatedEnvelope.varUInt(8);
        truncatedEnvelope.varUInt(1);
        truncatedEnvelope.varUInt(0);
        assertMalformed(
                truncatedEnvelope,
                definition(FileGdbLayerType.MULTIPOINT, false, false));

        Bytes truncatedXy = multiPointPrefix(false, false, 1);
        assertMalformed(truncatedXy, definition(FileGdbLayerType.MULTIPOINT, false, false));

        Bytes truncatedZ = multiPointPrefix(true, false, 1);
        truncatedZ.varInt(0);
        truncatedZ.varInt(0);
        assertMalformed(truncatedZ, definition(FileGdbLayerType.MULTIPOINT, true, false));

        Bytes truncatedM = multiPointPrefix(false, true, 1);
        truncatedM.varInt(0);
        truncatedM.varInt(0);
        assertMalformed(truncatedM, definition(FileGdbLayerType.MULTIPOINT, false, true));

        Bytes overflow = multiPointPrefix(false, false, 2);
        overflow.varInt(Long.MAX_VALUE);
        overflow.varInt(0);
        overflow.varInt(1);
        overflow.varInt(0);
        assertMalformed(overflow, definition(FileGdbLayerType.MULTIPOINT, false, false));

        Bytes trailing = multiPointPrefix(false, false, 1);
        trailing.varInt(0);
        trailing.varInt(0);
        trailing.u8(0);
        assertMalformed(trailing, definition(FileGdbLayerType.MULTIPOINT, false, false));

        for (FileGdbLayerType layerType : List.of(FileGdbLayerType.MULTIPOINT, FileGdbLayerType.POLYLINE)) {
            Bytes curve = new Bytes();
            curve.varUInt((layerType == FileGdbLayerType.MULTIPOINT ? 8 : 3) | CURVE_DESCRIPTION_FLAG);
            assertCode(
                    FileGdbErrorCode.UNSUPPORTED_FORMAT,
                    curve,
                    definition(layerType, false, false),
                    FileGdbReadLimits.defaults());
        }
    }

    private static Bytes multiPointPrefix(boolean hasZ, boolean hasM, int pointCount) {
        Bytes bytes = new Bytes();
        bytes.varUInt(8);
        bytes.varUInt(pointCount);
        if (pointCount > 0) {
            writeZeroEnvelope(bytes);
        }
        return bytes;
    }

    private static Bytes multiPartPrefix(int pointCount, int partCount, int... explicitPartSizes) {
        Bytes bytes = new Bytes();
        bytes.varUInt(3);
        bytes.varUInt(pointCount);
        if (pointCount > 0) {
            bytes.varUInt(partCount);
            if (partCount > 0 && partCount <= pointCount) {
                writeZeroEnvelope(bytes);
                for (int size : explicitPartSizes) {
                    bytes.varUInt(size);
                }
            }
        }
        return bytes;
    }

    private static void writeZeroEnvelope(Bytes bytes) {
        bytes.varUInt(0);
        bytes.varUInt(0);
        bytes.varUInt(0);
        bytes.varUInt(0);
    }

    private static void assertMalformed(Bytes bytes) {
        assertMalformed(bytes, definition(FileGdbLayerType.POLYLINE, false, false));
    }

    private static void assertMalformed(Bytes bytes, GdbTableDefinition definition) {
        assertCode(FileGdbErrorCode.MALFORMED_HEADER, bytes, definition, FileGdbReadLimits.defaults());
    }

    private static void assertCode(
            FileGdbErrorCode expected,
            Bytes bytes,
            GdbTableDefinition definition,
            FileGdbReadLimits limits) {
        FileGdbException exception = assertThrows(
                FileGdbException.class,
                () -> GdbGeometryReader.read(ByteBuffer.wrap(bytes.toByteArray()), definition, limits, 7));
        assertEquals(expected, exception.code());
    }

    private static GdbTableDefinition definition(
            FileGdbLayerType layerType,
            boolean hasZ,
            boolean hasM) {
        int geometryType = switch (layerType) {
            case MULTIPOINT -> 2;
            case POLYLINE -> 3;
            default -> throw new IllegalArgumentException("Unsupported test layer type " + layerType);
        };
        int properties = (hasZ ? 0x80 : 0) | (hasM ? 0x40 : 0);
        FileGdbSpatialReference spatialReference = new FileGdbSpatialReference(
                "",
                hasZ,
                hasM,
                0,
                0,
                1,
                hasZ ? 0d : null,
                hasZ ? 1d : null,
                hasM ? 0d : null,
                hasM ? 1d : null,
                0,
                hasZ ? 0d : null,
                hasM ? 0d : null,
                new FileGdbEnvelope(0, 0, 0, 0),
                null,
                null,
                null,
                null);
        return new GdbTableDefinition(
                "a00000009",
                "a00000009.gdbtable",
                1,
                1_024,
                geometryType,
                properties,
                layerType,
                List.of(),
                spatialReference);
    }

    private static FileGdbReadLimits limits(int maxParts, int maxPoints) {
        FileGdbReadLimits defaults = FileGdbReadLimits.defaults();
        return new FileGdbReadLimits(
                defaults.maxTableFileBytes(),
                defaults.maxFields(),
                defaults.maxRecordBytes(),
                defaults.maxStringBytes(),
                defaults.maxBinaryBytes(),
                maxParts,
                maxPoints,
                defaults.maxIndexSlotsPerCursor(),
                defaults.maxFeaturesPerCursor());
    }

    private static final class Bytes {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        void u8(long value) {
            output.write((int) value & 0xff);
        }

        void varUInt(long value) {
            do {
                int current = (int) (value & 0x7f);
                value >>>= 7;
                u8(value == 0 ? current : current | 0x80);
            } while (value != 0);
        }

        void varInt(long value) {
            long magnitude = Math.abs(value);
            int first = (int) (magnitude & 0x3f);
            magnitude >>>= 6;
            if (value < 0) {
                first |= 0x40;
            }
            u8(magnitude == 0 ? first : first | 0x80);
            while (magnitude != 0) {
                int current = (int) (magnitude & 0x7f);
                magnitude >>>= 7;
                u8(magnitude == 0 ? current : current | 0x80);
            }
        }

        byte[] toByteArray() {
            return output.toByteArray();
        }
    }
}
