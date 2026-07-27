package cn.superhuang.data.scalpel.shapefile.s3;

import cn.superhuang.data.scalpel.shapefile.ShapefileComponent;
import cn.superhuang.data.scalpel.shapefile.ShapefileErrorCode;
import cn.superhuang.data.scalpel.shapefile.ShapefileException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class TestS3ObjectAccess implements S3ObjectAccess {
    final Map<ShapefileComponent, byte[]> objects = new EnumMap<>(ShapefileComponent.class);
    final Map<ShapefileComponent, Long> declaredSizes = new EnumMap<>(ShapefileComponent.class);
    final List<HeadCall> headCalls = new ArrayList<>();
    final List<RangeCall> rangeCalls = new ArrayList<>();
    String responseETag;
    String snapshotVersionId;
    String responseVersionId;
    String contentRangeOverride;
    boolean shortResponse;
    long totalLengthDelta;

    @Override
    public S3ObjectSnapshot head(
            ShapefileComponent component,
            String objectKey,
            boolean allowMissing) {
        headCalls.add(new HeadCall(component, objectKey, allowMissing));
        byte[] bytes = objects.get(component);
        if (bytes == null) {
            if (allowMissing) {
                return null;
            }
            throw new ShapefileException(
                    ShapefileErrorCode.MISSING_COMPONENT,
                    "Missing test object " + component);
        }
        return new S3ObjectSnapshot(
                objectKey,
                declaredSizes.getOrDefault(component, (long) bytes.length),
                etag(component),
                snapshotVersionId);
    }

    @Override
    public S3RangeResponse readRange(
            ShapefileComponent component,
            S3ObjectSnapshot snapshot,
            long startInclusive,
            long endInclusive) {
        rangeCalls.add(new RangeCall(component, startInclusive, endInclusive));
        byte[] source = objects.get(component);
        byte[] bytes = Arrays.copyOfRange(
                source,
                Math.toIntExact(startInclusive),
                Math.toIntExact(endInclusive + 1));
        if (shortResponse && bytes.length > 0) {
            bytes = Arrays.copyOf(bytes, bytes.length - 1);
        }
        String contentRange = contentRangeOverride != null
                ? contentRangeOverride
                : "bytes " + startInclusive + "-" + endInclusive + "/" + (source.length + totalLengthDelta);
        String eTag = responseETag != null ? responseETag : etag(component);
        return new S3RangeResponse(bytes, (long) bytes.length, contentRange, eTag, responseVersionId);
    }

    static String etag(ShapefileComponent component) {
        return "\"etag-" + component.name().toLowerCase(java.util.Locale.ROOT) + "\"";
    }

    record HeadCall(ShapefileComponent component, String key, boolean allowMissing) {
    }

    record RangeCall(ShapefileComponent component, long start, long end) {
    }
}
