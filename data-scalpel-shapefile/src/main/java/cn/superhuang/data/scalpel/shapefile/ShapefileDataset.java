package cn.superhuang.data.scalpel.shapefile;

import cn.superhuang.data.scalpel.shapefile.internal.DbfRecordReader;
import cn.superhuang.data.scalpel.shapefile.internal.DbfSchemaReader;
import cn.superhuang.data.scalpel.shapefile.internal.DbfTableDefinition;
import cn.superhuang.data.scalpel.shapefile.internal.LocalShapefileSource;
import cn.superhuang.data.scalpel.shapefile.internal.PrjReader;
import cn.superhuang.data.scalpel.shapefile.internal.RandomAccessObjectReader;
import cn.superhuang.data.scalpel.shapefile.internal.ShapefileFeatureCursorImpl;
import cn.superhuang.data.scalpel.shapefile.internal.ShapefileGeometryReader;
import cn.superhuang.data.scalpel.shapefile.internal.ShapefileHeader;
import cn.superhuang.data.scalpel.shapefile.internal.ShapefileHeaderReader;
import cn.superhuang.data.scalpel.shapefile.internal.ShapefileIndex;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileField;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileSchema;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileSpatialReference;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Entry point for one unpacked Shapefile component set.
 * A dataset instance and its cursors are not thread-safe.
 */
public final class ShapefileDataset implements AutoCloseable {
    private final ShapefileSource source;
    private final ShapefileSourceInfo sourceInfo;
    private final RandomAccessObjectReader shp;
    private final RandomAccessObjectReader shx;
    private final RandomAccessObjectReader dbf;
    private final ShapefileIndex index;
    private final DbfRecordReader dbfRecordReader;
    private final ShapefileGeometryReader geometryReader;
    private final ShapefileReadLimits limits;
    private final ShapefileSchema schema;
    private final Set<ShapefileFeatureCursor> cursors =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean closed;

    private ShapefileDataset(
            ShapefileSource source,
            ShapefileSourceInfo sourceInfo,
            RandomAccessObjectReader shp,
            RandomAccessObjectReader shx,
            RandomAccessObjectReader dbf,
            ShapefileIndex index,
            DbfRecordReader dbfRecordReader,
            ShapefileGeometryReader geometryReader,
            ShapefileReadLimits limits,
            ShapefileSchema schema) {
        this.source = source;
        this.sourceInfo = sourceInfo;
        this.shp = shp;
        this.shx = shx;
        this.dbf = dbf;
        this.index = index;
        this.dbfRecordReader = dbfRecordReader;
        this.geometryReader = geometryReader;
        this.limits = limits;
        this.schema = schema;
    }

    public static ShapefileDataset open(Path shapefilePath) {
        return open(shapefilePath, ShapefileOpenOptions.defaults());
    }

    public static ShapefileDataset open(Path shapefilePath, ShapefileOpenOptions options) {
        Objects.requireNonNull(options, "options");
        return open(LocalShapefileSource.open(shapefilePath, options), options);
    }

    public static ShapefileDataset open(ShapefileSource source) {
        return open(source, ShapefileOpenOptions.defaults());
    }

    /**
     * Opens a dataset and takes ownership of {@code source}. The source is closed when opening fails
     * or when the returned dataset is closed.
     */
    public static ShapefileDataset open(ShapefileSource source, ShapefileOpenOptions options) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(options, "options");
        RandomAccessObjectReader shp = null;
        RandomAccessObjectReader shx = null;
        RandomAccessObjectReader dbf = null;
        RandomAccessObjectReader cpg = null;
        RandomAccessObjectReader prj = null;
        try {
            ShapefileSourceInfo sourceInfo = Objects.requireNonNull(source.info(), "source.info()");
            shp = openComponent(source, ShapefileComponent.SHP, options.readLimits());
            shx = openComponent(source, ShapefileComponent.SHX, options.readLimits());
            dbf = openComponent(source, ShapefileComponent.DBF, options.readLimits());
            cpg = openComponent(source, ShapefileComponent.CPG, options.readLimits());
            prj = openComponent(source, ShapefileComponent.PRJ, options.readLimits());

            ShapefileHeader shpHeader = ShapefileHeaderReader.read(shp, "SHP");
            ShapefileHeader shxHeader = ShapefileHeaderReader.read(shx, "SHX");
            if (shpHeader.shapeType() != shxHeader.shapeType()
                    || !shpHeader.envelope().equals(shxHeader.envelope())) {
                throw new ShapefileException(
                        ShapefileErrorCode.MALFORMED_HEADER,
                        "SHP and SHX headers do not describe the same layer");
            }
            ShapefileIndex index = ShapefileIndex.open(shx, shp, options.readLimits());
            DbfTableDefinition dbfDefinition = DbfSchemaReader.read(dbf, cpg, options);
            if (dbfDefinition.recordCount() != index.recordCount()) {
                throw new ShapefileException(
                        ShapefileErrorCode.RECORD_MISMATCH,
                        "SHP/SHX and DBF record counts do not match");
            }
            ShapefileSpatialReference spatialReference =
                    PrjReader.read(prj, options.readLimits().maxMetadataBytes());
            closeMetadata(cpg);
            cpg = null;
            closeMetadata(prj);
            prj = null;

            List<ShapefileField> fields = dbfDefinition.fields().stream().map(value -> value.field()).toList();
            ShapefileSchema schema = new ShapefileSchema(
                    shpHeader.shapeType(),
                    index.recordCount(),
                    fields,
                    shpHeader.envelope(),
                    dbfDefinition.charset(),
                    spatialReference);
            DbfRecordReader dbfReader = new DbfRecordReader(dbf, dbfDefinition);
            ShapefileGeometryReader geometryReader = new ShapefileGeometryReader(
                    shp, index, shpHeader.shapeType(), options.readLimits());
            return new ShapefileDataset(
                    source,
                    sourceInfo,
                    shp,
                    shx,
                    dbf,
                    index,
                    dbfReader,
                    geometryReader,
                    options.readLimits(),
                    schema);
        } catch (RuntimeException exception) {
            closeAfterFailure(exception, prj, cpg, dbf, shx, shp, source);
            throw exception;
        }
    }

    public ShapefileSourceInfo sourceInfo() {
        ensureOpen();
        return sourceInfo;
    }

    public ShapefileSchema schema() {
        ensureOpen();
        return schema;
    }

    public ShapefileFeatureCursor openCursor(ShapefileReadOptions options) {
        ensureOpen();
        Objects.requireNonNull(options, "options");
        if (options.limit() > limits.maxFeaturesPerCursor()) {
            throw new ShapefileException(
                    ShapefileErrorCode.LIMIT_EXCEEDED,
                    "Cursor feature limit exceeds the configured maximum");
        }
        final ShapefileFeatureCursor[] holder = new ShapefileFeatureCursor[1];
        ShapefileFeatureCursor cursor = new ShapefileFeatureCursorImpl(
                index, dbfRecordReader, geometryReader, options.limit(), () -> cursors.remove(holder[0]));
        holder[0] = cursor;
        cursors.add(cursor);
        return cursor;
    }

    private static RandomAccessObjectReader openComponent(
            ShapefileSource source,
            ShapefileComponent component,
            ShapefileReadLimits limits) {
        if (!source.exists(component)) {
            if (component.required()) {
                throw new ShapefileException(
                        ShapefileErrorCode.MISSING_COMPONENT,
                        "Missing Shapefile ." + component.extension() + " component");
            }
            return null;
        }
        ShapefileRandomAccessObject object = source.open(component);
        if (object == null) {
            throw new ShapefileException(
                    ShapefileErrorCode.INVALID_SOURCE,
                    "Shapefile source returned no " + component + " component object");
        }
        try {
            RandomAccessObjectReader reader = new RandomAccessObjectReader(object, component.name());
            long maximum = component.required()
                    ? limits.maxComponentFileBytes()
                    : limits.maxMetadataBytes();
            if (reader.size() > maximum) {
                throw new ShapefileException(
                        ShapefileErrorCode.LIMIT_EXCEEDED,
                        "Shapefile " + component + " component exceeds the configured size limit");
            }
            return reader;
        } catch (RuntimeException exception) {
            try {
                object.close();
            } catch (RuntimeException closeFailure) {
                exception.addSuppressed(closeFailure);
            }
            throw exception;
        }
    }

    private static void closeMetadata(RandomAccessObjectReader reader) {
        if (reader != null) {
            reader.close();
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new ShapefileException(ShapefileErrorCode.CLOSED, "Shapefile dataset is closed");
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        RuntimeException failure = null;
        for (ShapefileFeatureCursor cursor : List.copyOf(cursors)) {
            failure = close(failure, cursor);
        }
        cursors.clear();
        failure = close(failure, dbf);
        failure = close(failure, shx);
        failure = close(failure, shp);
        failure = close(failure, source);
        if (failure != null) {
            throw failure;
        }
    }

    private static void closeAfterFailure(RuntimeException failure, AutoCloseable... resources) {
        for (AutoCloseable resource : resources) {
            if (resource == null) {
                continue;
            }
            try {
                resource.close();
            } catch (Exception closeFailure) {
                failure.addSuppressed(closeFailure);
            }
        }
    }

    private static RuntimeException close(RuntimeException failure, AutoCloseable resource) {
        try {
            resource.close();
        } catch (RuntimeException exception) {
            if (failure == null) {
                return exception;
            }
            failure.addSuppressed(exception);
        } catch (Exception exception) {
            ShapefileException wrapped = new ShapefileException(
                    ShapefileErrorCode.IO_ERROR, "Cannot close Shapefile resource", exception);
            if (failure == null) {
                return wrapped;
            }
            failure.addSuppressed(wrapped);
        }
        return failure;
    }
}
