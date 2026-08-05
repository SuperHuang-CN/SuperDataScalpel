package cn.superhuang.data.scalpel.business.filedataset.service.parse;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetField;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** Exact canonical-schema comparison and stable SHA-256 fingerprinting for table sources. */
@Component
public class FileDatasetSchemaValidator {

    public void requireCompatible(
            List<FileDatasetField> expected,
            FileDatasetParser.ParseResult actual,
            Map<String, Object> expectedSourceMetadata
    ) {
        List<FileDatasetParser.Field> actualFields = actual.fields();
        if (expected.size() != actualFields.size()) {
            throw mismatch("字段数量", expected.size(), actualFields.size());
        }
        for (int index = 0; index < expected.size(); index++) {
            FileDatasetField expectedField = expected.get(index);
            FileDatasetParser.Field actualField = actualFields.get(index);
            requireEqual(index, "名称", expectedField.getName(), actualField.name());
            requireEqual(index, "顺序", expectedField.getSortOrder(), actualField.sortOrder());
            requireEqual(index, "平台类型", expectedField.getTypeDefinition(), actualField.type());
            requireEqual(index, "nullable", expectedField.isNullable(), actualField.nullable());
        }
        requireShapefileMetadata(expectedSourceMetadata, actual.sourceMetadata());
    }

    public void requireCompatible(
            FileDatasetParser.ParseResult expected,
            FileDatasetParser.ParseResult actual
    ) {
        List<FileDatasetParser.Field> expectedFields = expected.fields();
        List<FileDatasetParser.Field> actualFields = actual.fields();
        if (expectedFields.size() != actualFields.size()) {
            throw mismatch("字段数量", expectedFields.size(), actualFields.size());
        }
        for (int index = 0; index < expectedFields.size(); index++) {
            FileDatasetParser.Field expectedField = expectedFields.get(index);
            FileDatasetParser.Field actualField = actualFields.get(index);
            requireEqual(index, "名称", expectedField.name(), actualField.name());
            requireEqual(index, "顺序", expectedField.sortOrder(), actualField.sortOrder());
            requireEqual(index, "平台类型", expectedField.type(), actualField.type());
            requireEqual(index, "nullable", expectedField.nullable(), actualField.nullable());
        }
        requireShapefileMetadata(expected.sourceMetadata(), actual.sourceMetadata());
    }

    public String fingerprint(List<FileDatasetParser.Field> fields) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (int index = 0; index < fields.size(); index++) {
                FileDatasetParser.Field field = fields.get(index);
                update(digest, Integer.toString(index));
                update(digest, field.name());
                update(digest, field.type().type().name());
                update(digest, value(field.type().length()));
                update(digest, value(field.type().precision()));
                update(digest, value(field.type().scale()));
                update(digest, Boolean.toString(field.nullable()));
                update(digest, field.type().geometry() == null ? "" : field.type().geometry().kind().name());
                update(digest, field.type().geometry() == null ? "" : field.type().geometry().crs().authority());
                update(digest, field.type().geometry() == null
                        ? "" : Integer.toString(field.type().geometry().crs().code()));
                update(digest, field.type().geometry() == null ? "" : field.type().geometry().dimension().name());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }

    private static void requireShapefileMetadata(
            Map<String, Object> expected,
            Map<String, Object> actual
    ) {
        if (expected == null || !expected.containsKey("shapeType")) {
            return;
        }
        requireMetadataEqual("Shape 类型", expected, actual, "shapeType");
        requireMetadataEqual("Z 维度", expected, actual, "shapeHasZ");
        requireMetadataEqual("M 维度", expected, actual, "shapeHasM");
        requireMetadataEqual("Geometry 字段", expected, actual, "geometryField");
        requireMetadataEqual("CRS authority", expected, actual, "crsAuthority");
        requireMetadataEqual("CRS code", expected, actual, "crsCode");
        requireMetadataEqual("坐标维度", expected, actual, "coordinateDimension");
        requireMetadataEqual("Geometry kind", expected, actual, "geometryKind");
        Object expectedSpatialReference = expected.get("spatialReference");
        Object actualSpatialReference = actual.get("spatialReference");
        String expectedWkt = normalizedWkt(expectedSpatialReference);
        String actualWkt = normalizedWkt(actualSpatialReference);
        if (!java.util.Objects.equals(expectedWkt, actualWkt)) {
            throw mismatch("SHP PRJ WKT", expectedWkt, actualWkt);
        }
    }

    private static String normalizedWkt(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        Object normalized = map.get("normalizedWkt");
        Object wkt = normalized == null ? map.get("wkt") : normalized;
        return wkt == null ? null : String.valueOf(wkt).trim().replaceAll("\\s+", " ");
    }

    private static void requireMetadataEqual(
            String label,
            Map<String, Object> expected,
            Map<String, Object> actual,
            String key
    ) {
        if (!java.util.Objects.equals(expected.get(key), actual.get(key))) {
            throw mismatch(label, expected.get(key), actual.get(key));
        }
    }

    private static void requireEqual(int index, String label, Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw mismatch("第 " + (index + 1) + " 个字段" + label, expected, actual);
        }
    }

    private static FileDatasetParsingException mismatch(String label, Object expected, Object actual) {
        return new FileDatasetParsingException(
                "Schema 不一致：" + label + "，期望=" + display(expected) + "，实际=" + display(actual)
        );
    }

    private static String display(Object value) {
        String text = String.valueOf(value);
        return text.length() <= 300 ? text : text.substring(0, 300) + "...";
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static String value(Integer value) {
        return value == null ? "" : value.toString();
    }
}
