package cn.superhuang.data.scalpel.filegdb.model.geometry;

/** Geometry decoded in its source coordinate system. */
public sealed interface FileGdbGeometry
        permits FileGdbPoint, FileGdbMultiPoint, FileGdbPolyline, FileGdbPolygon {
    boolean hasZ();

    boolean hasM();
}
