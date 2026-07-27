package cn.superhuang.data.scalpel.shapefile.internal;

import cn.superhuang.data.scalpel.shapefile.ShapefileErrorCode;
import cn.superhuang.data.scalpel.shapefile.ShapefileException;
import cn.superhuang.data.scalpel.shapefile.ShapefileFeatureCursor;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileFeature;
import cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefileGeometry;
import java.util.NoSuchElementException;

public final class ShapefileFeatureCursorImpl implements ShapefileFeatureCursor {
    private final ShapefileIndex index;
    private final DbfRecordReader dbfReader;
    private final ShapefileGeometryReader geometryReader;
    private final int limit;
    private final Runnable onClose;

    private long nextSlot;
    private int returned;
    private ShapefileFeature prefetched;
    private ShapefileException failure;
    private boolean closed;

    public ShapefileFeatureCursorImpl(
            ShapefileIndex index,
            DbfRecordReader dbfReader,
            ShapefileGeometryReader geometryReader,
            int limit,
            Runnable onClose) {
        this.index = index;
        this.dbfReader = dbfReader;
        this.geometryReader = geometryReader;
        this.limit = limit;
        this.onClose = onClose;
    }

    @Override
    public boolean hasNext() {
        ensureUsable();
        if (prefetched != null) {
            return true;
        }
        if (returned >= limit) {
            return false;
        }
        try {
            while (nextSlot < index.recordCount()) {
                long slot = nextSlot++;
                ShapefileGeometry geometry = geometryReader.read(slot);
                DbfRecordReader.Record dbfRecord = dbfReader.read(slot);
                if (dbfRecord.deleted()) {
                    continue;
                }
                prefetched = new ShapefileFeature(slot + 1, dbfRecord.attributes(), geometry);
                return true;
            }
            return false;
        } catch (ShapefileException exception) {
            failure = exception;
            throw exception;
        } catch (RuntimeException exception) {
            failure = new ShapefileException(
                    ShapefileErrorCode.RECORD_MISMATCH,
                    "Cannot decode Shapefile record " + nextSlot,
                    exception);
            throw failure;
        }
    }

    @Override
    public ShapefileFeature next() {
        if (!hasNext()) {
            throw new NoSuchElementException("Shapefile cursor has no more features");
        }
        ShapefileFeature result = prefetched;
        prefetched = null;
        returned++;
        return result;
    }

    private void ensureUsable() {
        if (closed) {
            throw new ShapefileException(ShapefileErrorCode.CLOSED, "Shapefile cursor is closed");
        }
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        prefetched = null;
        onClose.run();
    }
}
