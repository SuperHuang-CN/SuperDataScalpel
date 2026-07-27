package cn.superhuang.data.scalpel.shapefile;

import java.nio.charset.Charset;
import java.util.Objects;

/** Options used while resolving and opening one local Shapefile component set. */
public record ShapefileOpenOptions(
        ShapefileReadLimits readLimits,
        Charset dbfCharsetOverride,
        Charset dbfFallbackCharset,
        boolean allowSymbolicLinks) {

    public ShapefileOpenOptions {
        Objects.requireNonNull(readLimits, "readLimits");
        Objects.requireNonNull(dbfFallbackCharset, "dbfFallbackCharset");
    }

    public static ShapefileOpenOptions defaults() {
        return new ShapefileOpenOptions(
                ShapefileReadLimits.defaults(), null, Charset.forName("GB18030"), false);
    }
}
