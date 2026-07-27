package cn.superhuang.data.scalpel.filegdb.internal;

import cn.superhuang.data.scalpel.filegdb.FileGdbErrorCode;
import cn.superhuang.data.scalpel.filegdb.FileGdbException;
import cn.superhuang.data.scalpel.filegdb.FileGdbFeatureCursor;
import cn.superhuang.data.scalpel.filegdb.FileGdbReadLimits;
import cn.superhuang.data.scalpel.filegdb.FileGdbSource;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbLayer;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbSchema;

/** Internal bridge between catalog metadata and resource-owning cursors. */
public final class GdbLayerHandle {
    private final FileGdbLayer layer;
    private final FileGdbSchema schema;
    private final FileGdbSource source;
    private final GdbTableDefinition definition;
    private final String indexFileName;
    private final FileGdbReadLimits limits;

    GdbLayerHandle(
            FileGdbLayer layer,
            FileGdbSource source,
            GdbTableDefinition definition,
            String indexFileName,
            FileGdbReadLimits limits) {
        this.layer = layer;
        this.source = source;
        this.definition = definition;
        this.indexFileName = indexFileName;
        this.limits = limits;
        this.schema = new FileGdbSchema(
                layer.id(),
                layer.name(),
                layer.type(),
                definition.fields().stream().map(GdbFieldDefinition::publicField).toList(),
                definition.spatialReference());
    }

    public FileGdbLayer layer() {
        return layer;
    }

    public FileGdbSchema schema() {
        return schema;
    }

    public FileGdbFeatureCursor openCursor(int limit, Runnable closeCallback) {
        if (limit > limits.maxFeaturesPerCursor()) {
            throw new FileGdbException(
                    FileGdbErrorCode.LIMIT_EXCEEDED,
                    "Cursor feature limit exceeds the configured maximum");
        }
        if (!layer.type().isCursorReadable()) {
            throw new FileGdbException(
                    FileGdbErrorCode.UNSUPPORTED_FORMAT,
                    "Layer " + layer.name() + " uses unsupported geometry type " + layer.type());
        }
        GdbTableIndexReader indexReader = GdbTableIndexReader.open(
                source,
                indexFileName,
                definition.physicalName(),
                limits);
        try {
            GdbTableReader tableReader = GdbTableReader.open(source, definition, limits);
            return new GdbFeatureCursorImpl(
                    definition,
                    indexReader,
                    tableReader,
                    limits,
                    limit,
                    closeCallback);
        } catch (RuntimeException exception) {
            indexReader.close();
            throw exception;
        }
    }
}
