package cn.superhuang.data.scalpel.shapefile.model;

import cn.superhuang.data.scalpel.shapefile.ShapefileErrorCode;
import cn.superhuang.data.scalpel.shapefile.ShapefileException;

/** Exact shape type stored in the SHP/SHX header. */
public enum ShapefileShapeType {
    NULL(0, false, false, true),
    POINT(1, false, false, true),
    POLYLINE(3, false, false, true),
    POLYGON(5, false, false, true),
    MULTIPOINT(8, false, false, true),
    POINT_Z(11, true, true, true),
    POLYLINE_Z(13, true, true, true),
    POLYGON_Z(15, true, true, true),
    MULTIPOINT_Z(18, true, true, true),
    POINT_M(21, false, true, true),
    POLYLINE_M(23, false, true, true),
    POLYGON_M(25, false, true, true),
    MULTIPOINT_M(28, false, true, true),
    MULTIPATCH(31, true, true, false);

    private final int code;
    private final boolean hasZ;
    private final boolean hasM;
    private final boolean readable;

    ShapefileShapeType(int code, boolean hasZ, boolean hasM, boolean readable) {
        this.code = code;
        this.hasZ = hasZ;
        this.hasM = hasM;
        this.readable = readable;
    }

    public int code() {
        return code;
    }

    public boolean hasZ() {
        return hasZ;
    }

    public boolean hasM() {
        return hasM;
    }

    public boolean readable() {
        return readable;
    }

    public static ShapefileShapeType fromCode(int code) {
        for (ShapefileShapeType value : values()) {
            if (value.code == code) {
                if (!value.readable) {
                    throw new ShapefileException(
                            ShapefileErrorCode.UNSUPPORTED_FORMAT, "MultiPatch geometry is not supported");
                }
                return value;
            }
        }
        throw new ShapefileException(
                ShapefileErrorCode.UNSUPPORTED_FORMAT, "Unsupported Shapefile shape type " + code);
    }
}
