package cn.superhuang.data.scalpel.shapefile;

/** Stable storage categories exposed without leaking adapter-specific implementation types. */
public enum ShapefileSourceType {
    LOCAL,
    S3,
    CUSTOM
}
