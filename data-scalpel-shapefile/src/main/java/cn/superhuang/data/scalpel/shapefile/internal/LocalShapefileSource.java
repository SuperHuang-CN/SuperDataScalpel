package cn.superhuang.data.scalpel.shapefile.internal;

import cn.superhuang.data.scalpel.shapefile.ShapefileComponent;
import cn.superhuang.data.scalpel.shapefile.ShapefileErrorCode;
import cn.superhuang.data.scalpel.shapefile.ShapefileException;
import cn.superhuang.data.scalpel.shapefile.ShapefileOpenOptions;
import cn.superhuang.data.scalpel.shapefile.ShapefileRandomAccessObject;
import cn.superhuang.data.scalpel.shapefile.ShapefileSource;
import cn.superhuang.data.scalpel.shapefile.ShapefileSourceInfo;
import cn.superhuang.data.scalpel.shapefile.ShapefileSourceType;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Local filesystem source retaining the existing sibling, case, and link checks. */
public final class LocalShapefileSource implements ShapefileSource {
    private final Map<ShapefileComponent, Path> components;
    private final ShapefileSourceInfo info;
    private final Set<LocalObject> openObjects = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean closed;

    private LocalShapefileSource(Map<ShapefileComponent, Path> components, Path shp) {
        this.components = Map.copyOf(components);
        this.info = new ShapefileSourceInfo(ShapefileSourceType.LOCAL, shp.toString());
    }

    public static LocalShapefileSource open(Path path, ShapefileOpenOptions options) {
        LocalShapefileComponents resolved = LocalShapefileComponents.resolve(path, options);
        EnumMap<ShapefileComponent, Path> components = new EnumMap<>(ShapefileComponent.class);
        components.put(ShapefileComponent.SHP, resolved.shp());
        components.put(ShapefileComponent.SHX, resolved.shx());
        components.put(ShapefileComponent.DBF, resolved.dbf());
        if (resolved.cpg() != null) {
            components.put(ShapefileComponent.CPG, resolved.cpg());
        }
        if (resolved.prj() != null) {
            components.put(ShapefileComponent.PRJ, resolved.prj());
        }
        return new LocalShapefileSource(components, resolved.shp());
    }

    @Override
    public ShapefileSourceInfo info() {
        ensureOpen();
        return info;
    }

    @Override
    public boolean exists(ShapefileComponent component) {
        ensureOpen();
        if (component == null) {
            throw new NullPointerException("component");
        }
        return components.containsKey(component);
    }

    @Override
    public ShapefileRandomAccessObject open(ShapefileComponent component) {
        ensureOpen();
        if (component == null) {
            throw new NullPointerException("component");
        }
        Path path = components.get(component);
        if (path == null) {
            throw new ShapefileException(
                    ShapefileErrorCode.MISSING_COMPONENT,
                    "Missing Shapefile ." + component.extension() + " component");
        }
        try {
            FileChannel channel = FileChannel.open(path, StandardOpenOption.READ);
            LocalObject object = new LocalObject(channel, component, channel.size());
            openObjects.add(object);
            return object;
        } catch (IOException exception) {
            throw new ShapefileException(
                    ShapefileErrorCode.IO_ERROR,
                    "Cannot open Shapefile " + component + " component",
                    exception);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new ShapefileException(ShapefileErrorCode.CLOSED, "Local Shapefile source is closed");
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        RuntimeException failure = null;
        for (LocalObject object : List.copyOf(openObjects)) {
            try {
                object.close();
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        openObjects.clear();
        if (failure != null) {
            throw failure;
        }
    }

    private final class LocalObject implements ShapefileRandomAccessObject {
        private final FileChannel channel;
        private final ShapefileComponent component;
        private final long size;
        private boolean objectClosed;

        private LocalObject(FileChannel channel, ShapefileComponent component, long size) {
            this.channel = channel;
            this.component = component;
            this.size = size;
        }

        @Override
        public long size() {
            ensureObjectOpen();
            return size;
        }

        @Override
        public int read(long position, ByteBuffer target) {
            ensureObjectOpen();
            if (position < 0) {
                throw new ShapefileException(
                        ShapefileErrorCode.INVALID_OFFSET,
                        component + " component has a negative read position");
            }
            try {
                return channel.read(target, position);
            } catch (IOException exception) {
                throw new ShapefileException(
                        ShapefileErrorCode.IO_ERROR,
                        "Cannot read Shapefile " + component + " component",
                        exception);
            }
        }

        private void ensureObjectOpen() {
            if (objectClosed || LocalShapefileSource.this.closed) {
                throw new ShapefileException(ShapefileErrorCode.CLOSED, component + " component is closed");
            }
        }

        @Override
        public void close() {
            if (objectClosed) {
                return;
            }
            objectClosed = true;
            openObjects.remove(this);
            try {
                channel.close();
            } catch (IOException exception) {
                throw new ShapefileException(
                        ShapefileErrorCode.IO_ERROR,
                        "Cannot close Shapefile " + component + " component",
                        exception);
            }
        }
    }
}
