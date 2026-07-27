package cn.superhuang.data.scalpel.filegdb.model;

import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbGeometry;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** One immutable logical feature; binary attribute values are copied on construction and access. */
public final class FileGdbFeature {
    private final int oid;
    private final Map<String, Object> attributes;
    private final FileGdbGeometry geometry;

    public FileGdbFeature(int oid, Map<String, Object> attributes, FileGdbGeometry geometry) {
        if (oid <= 0) {
            throw new IllegalArgumentException("oid must be positive");
        }
        this.oid = oid;
        this.attributes = immutableCopy(attributes);
        this.geometry = geometry;
    }

    public int oid() {
        return oid;
    }

    public Map<String, Object> attributes() {
        return immutableCopy(attributes);
    }

    public Object attribute(String name) {
        Object value = attributes.get(name);
        return copyValue(value);
    }

    public FileGdbGeometry geometry() {
        return geometry;
    }

    private static Map<String, Object> immutableCopy(Map<String, Object> source) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, copyValue(value)));
        return Collections.unmodifiableMap(result);
    }

    private static Object copyValue(Object value) {
        return value instanceof byte[] bytes ? bytes.clone() : value;
    }
}
