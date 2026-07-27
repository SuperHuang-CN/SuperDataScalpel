package cn.superhuang.data.scalpel.shapefile.model;

/** Uninterpreted WKT loaded from an optional sibling .prj file. */
public record ShapefileSpatialReference(String wkt) {
    public ShapefileSpatialReference {
        if (wkt == null || wkt.isBlank()) {
            throw new IllegalArgumentException("wkt must not be blank");
        }
    }
}
