package cn.superhuang.data.scalpel.filegdb.model;

/** Logical table or Esri feature-class geometry category. */
public enum FileGdbLayerType {
    TABLE(true),
    POINT(true),
    POLYGON(true),
    MULTIPOINT(true),
    POLYLINE(true),
    MULTIPATCH(false),
    UNKNOWN(false);

    private final boolean cursorReadable;

    FileGdbLayerType(boolean cursorReadable) {
        this.cursorReadable = cursorReadable;
    }

    public boolean isCursorReadable() {
        return cursorReadable;
    }
}
