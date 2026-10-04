package cn.superhuang.data.scalpel.business.spatialpreview.service;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Immutable source snapshot, read outside the management transaction; fresh revision checked before publication. */
public record SpatialPreviewSource(String key, String revision, Long estimatedRows, boolean updating,
                                   Reader reader, Supplier<String> currentRevision) {
    /** Files already decoded a geometry; avoid serializing and immediately decoding it again. */
    public interface Sink extends Consumer<byte[]> {
        void acceptGeometry(org.locationtech.jts.geom.Geometry geometry);
    }
    @FunctionalInterface
    public interface Reader { void read(Sink sink) throws Exception; }
}
