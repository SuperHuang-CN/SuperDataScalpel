package cn.superhuang.data.scalpel.shapefile.model.geometry;

/** Geometry decoded without coordinate conversion or repair. */
public sealed interface ShapefileGeometry permits
        ShapefilePoint, ShapefileMultiPoint, ShapefilePolyline, ShapefilePolygon {
    boolean hasZ();

    boolean hasM();
}
