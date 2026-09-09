package cn.superhuang.data.scalpel.business.filedataset.service.parse;

import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared Feature-level validation for GeoJSON document and line-oriented inputs. */
final class GeoJsonFeatureSupport {

    static final String FEATURE_ID_FIELD = "_feature_id";
    static final String GEOMETRY_FIELD = "geometry";

    private GeoJsonFeatureSupport() {
    }

    static Feature readFeature(
            JsonParser parser,
            String featurePath,
            boolean validateGeometry
    ) throws IOException {
        require(parser.currentToken(), JsonToken.START_OBJECT, featurePath, "必须是 Feature 对象");
        String type = null;
        String id = null;
        Map<String, Object> properties = null;
        GeometryKind geometryKind = null;
        boolean typeSeen = false;
        boolean idSeen = false;
        boolean propertiesSeen = false;
        boolean geometrySeen = false;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.currentToken() != JsonToken.PROPERTY_NAME) {
                throw invalid(featurePath, "Feature 对象结构无效");
            }
            String name = parser.currentName();
            JsonToken token = parser.nextToken();
            switch (name) {
                case "type" -> {
                    if (typeSeen) throw invalid(featurePath, "type 重复");
                    typeSeen = true;
                    require(token, JsonToken.VALUE_STRING, featurePath, "type 必须是字符串");
                    type = parser.getText();
                }
                case "id" -> {
                    if (idSeen) throw invalid(featurePath, "id 重复");
                    idSeen = true;
                    id = featureId(parser, token, featurePath);
                }
                case "properties" -> {
                    if (propertiesSeen) throw invalid(featurePath, "properties 重复");
                    propertiesSeen = true;
                    properties = properties(parser, token, featurePath);
                }
                case "geometry" -> {
                    if (geometrySeen) throw invalid(featurePath, "geometry 重复");
                    geometrySeen = true;
                    if (token == JsonToken.VALUE_NULL) {
                        continue;
                    }
                    if (validateGeometry) {
                        geometryKind = validateGeometry(parser.readValueAs(Object.class), featurePath, "geometry");
                    } else {
                        parser.skipChildren();
                    }
                }
                default -> parser.skipChildren();
            }
        }
        if (!"Feature".equals(type)) {
            throw invalid(featurePath, "type 必须为 Feature");
        }
        if (!propertiesSeen) {
            throw invalid(featurePath, "缺少 properties");
        }
        if (!geometrySeen) {
            throw invalid(featurePath, "缺少 geometry");
        }
        ensureNoReservedProperty(properties, featurePath);
        return new Feature(properties, id, geometryKind);
    }

    private static Map<String, Object> properties(JsonParser parser, JsonToken token, String featurePath)
            throws IOException {
        if (token == JsonToken.VALUE_NULL) {
            return Map.of();
        }
        require(token, JsonToken.START_OBJECT, featurePath, "properties 必须是对象或 null");
        Object value = parser.readValueAs(Object.class);
        if (!(value instanceof Map<?, ?> values)) {
            throw invalid(featurePath, "properties 必须是对象或 null");
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        values.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private static String featureId(JsonParser parser, JsonToken token, String featurePath) throws IOException {
        if (token == JsonToken.VALUE_NULL) return null;
        if (token == JsonToken.VALUE_STRING || token.isNumeric()) {
            return parser.getText();
        }
        throw invalid(featurePath, "id 必须是字符串、数值或 null");
    }

    private static void ensureNoReservedProperty(Map<String, Object> properties, String featurePath) {
        for (String name : properties.keySet()) {
            if (FEATURE_ID_FIELD.equals(name) || GEOMETRY_FIELD.equals(name)) {
                throw invalid(featurePath, "properties 不允许使用保留字段：" + name);
            }
        }
    }

    private static GeometryKind validateGeometry(Object value, String featurePath, String path) {
        if (!(value instanceof Map<?, ?> values)) {
            throw invalid(featurePath, path + " 必须是 Geometry 对象或 null");
        }
        Object rawType = values.get("type");
        if (!(rawType instanceof String text)) {
            throw invalid(featurePath, path + ".type 必须是字符串");
        }
        GeometryKind kind = geometryKind(text, featurePath, path);
        if (kind == GeometryKind.GEOMETRYCOLLECTION) {
            Object geometries = values.get("geometries");
            if (!(geometries instanceof List<?> items)) {
                throw invalid(featurePath, path + ".geometries 必须是数组");
            }
            for (int index = 0; index < items.size(); index++) {
                validateGeometry(items.get(index), featurePath, path + ".geometries[" + index + "]");
            }
            return kind;
        }
        Object coordinates = values.get("coordinates");
        if (!(coordinates instanceof List<?> items)) {
            throw invalid(featurePath, path + ".coordinates 必须是数组");
        }
        validateCoordinates(kind, items, featurePath, path + ".coordinates");
        return kind;
    }

    private static GeometryKind geometryKind(String type, String featurePath, String path) {
        return switch (type) {
            case "Point" -> GeometryKind.POINT;
            case "LineString" -> GeometryKind.LINESTRING;
            case "Polygon" -> GeometryKind.POLYGON;
            case "MultiPoint" -> GeometryKind.MULTIPOINT;
            case "MultiLineString" -> GeometryKind.MULTILINESTRING;
            case "MultiPolygon" -> GeometryKind.MULTIPOLYGON;
            case "GeometryCollection" -> GeometryKind.GEOMETRYCOLLECTION;
            default -> throw invalid(featurePath, path + ".type 不支持：" + type);
        };
    }

    private static void validateCoordinates(GeometryKind kind, List<?> coordinates, String featurePath, String path) {
        switch (kind) {
            case POINT -> position(coordinates, featurePath, path);
            case LINESTRING -> line(coordinates, featurePath, path);
            case POLYGON -> polygon(coordinates, featurePath, path);
            case MULTIPOINT -> {
                requireNonEmpty(coordinates, featurePath, path);
                for (int index = 0; index < coordinates.size(); index++) {
                    position(asList(coordinates.get(index), featurePath, path + "[" + index + "]"), featurePath,
                            path + "[" + index + "]");
                }
            }
            case MULTILINESTRING -> {
                requireNonEmpty(coordinates, featurePath, path);
                for (int index = 0; index < coordinates.size(); index++) {
                    line(asList(coordinates.get(index), featurePath, path + "[" + index + "]"), featurePath,
                            path + "[" + index + "]");
                }
            }
            case MULTIPOLYGON -> {
                requireNonEmpty(coordinates, featurePath, path);
                for (int index = 0; index < coordinates.size(); index++) {
                    polygon(asList(coordinates.get(index), featurePath, path + "[" + index + "]"), featurePath,
                            path + "[" + index + "]");
                }
            }
            case GEOMETRY, GEOMETRYCOLLECTION -> throw invalid(featurePath, path + " Geometry 坐标类型无效");
        }
    }

    private static void line(List<?> positions, String featurePath, String path) {
        if (positions.size() < 2) {
            throw invalid(featurePath, path + " 线至少需要两个坐标");
        }
        for (int index = 0; index < positions.size(); index++) {
            position(asList(positions.get(index), featurePath, path + "[" + index + "]"), featurePath,
                    path + "[" + index + "]");
        }
    }

    private static void polygon(List<?> rings, String featurePath, String path) {
        requireNonEmpty(rings, featurePath, path);
        for (int index = 0; index < rings.size(); index++) {
            String ringPath = path + "[" + index + "]";
            List<?> ring = asList(rings.get(index), featurePath, ringPath);
            if (ring.size() < 4) {
                throw invalid(featurePath, ringPath + " 面环至少需要四个坐标");
            }
            List<?> first = asList(ring.getFirst(), featurePath, ringPath + "[0]");
            List<?> last = asList(ring.getLast(), featurePath, ringPath + "[" + (ring.size() - 1) + "]");
            position(first, featurePath, ringPath + "[0]");
            position(last, featurePath, ringPath + "[" + (ring.size() - 1) + "]");
            if (!samePosition(first, last)) {
                throw invalid(featurePath, ringPath + " 面环必须闭合");
            }
            for (int point = 1; point < ring.size() - 1; point++) {
                position(asList(ring.get(point), featurePath, ringPath + "[" + point + "]"), featurePath,
                        ringPath + "[" + point + "]");
            }
        }
    }

    private static boolean samePosition(List<?> first, List<?> last) {
        return first.size() == 2 && last.size() == 2
                && Double.compare(((Number) first.getFirst()).doubleValue(), ((Number) last.getFirst()).doubleValue()) == 0
                && Double.compare(((Number) first.getLast()).doubleValue(), ((Number) last.getLast()).doubleValue()) == 0;
    }

    private static void position(List<?> value, String featurePath, String path) {
        if (value.size() != 2) {
            throw invalid(featurePath, path + " 坐标必须恰好包含 X 和 Y 两个值");
        }
        finite(value.getFirst(), featurePath, path + "[0]");
        finite(value.getLast(), featurePath, path + "[1]");
    }

    private static void finite(Object value, String featurePath, String path) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw invalid(featurePath, path + " 坐标必须是有限数值");
        }
    }

    private static List<?> asList(Object value, String featurePath, String path) {
        if (value instanceof List<?> list) return list;
        throw invalid(featurePath, path + " 必须是数组");
    }

    private static void requireNonEmpty(List<?> values, String featurePath, String path) {
        if (values.isEmpty()) {
            throw invalid(featurePath, path + " 不能为空");
        }
    }

    private static void require(JsonToken actual, JsonToken expected, String featurePath, String message) {
        if (actual != expected) throw invalid(featurePath, message);
    }

    private static FileDatasetParsingException invalid(String featurePath, String message) {
        return new FileDatasetParsingException(featurePath + " " + message);
    }

    record Feature(Map<String, Object> properties, String id, GeometryKind geometryKind) {
    }
}
