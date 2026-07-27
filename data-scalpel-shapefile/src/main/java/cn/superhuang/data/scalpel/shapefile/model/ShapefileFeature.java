package cn.superhuang.data.scalpel.shapefile.model;

import cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefileGeometry;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** One immutable active record with its source record number. */
public final class ShapefileFeature {
    private final long recordNumber;
    private final Map<String, Object> attributes;
    private final ShapefileGeometry geometry;

    public ShapefileFeature(long recordNumber, Map<String, Object> attributes, ShapefileGeometry geometry) {
        if (recordNumber <= 0) {
            throw new IllegalArgumentException("recordNumber must be positive");
        }
        this.recordNumber = recordNumber;
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
        this.geometry = geometry;
    }

    public long recordNumber() {
        return recordNumber;
    }

    public Map<String, Object> attributes() {
        return attributes;
    }

    public Object attribute(String name) {
        return attributes.get(name);
    }

    public ShapefileGeometry geometry() {
        return geometry;
    }
}
