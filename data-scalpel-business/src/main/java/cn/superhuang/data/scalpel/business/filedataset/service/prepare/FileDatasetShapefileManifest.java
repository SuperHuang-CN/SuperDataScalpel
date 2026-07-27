package cn.superhuang.data.scalpel.business.filedataset.service.prepare;

import cn.superhuang.data.scalpel.shapefile.ShapefileComponent;

import java.util.EnumSet;
import java.util.List;

/** Completion marker and exact component inventory for one materialized Shapefile. */
public record FileDatasetShapefileManifest(
        int version,
        String format,
        String sourceName,
        List<ComponentEntry> components
) {
    public static final int CURRENT_VERSION = 1;
    public static final String FORMAT = "SHP";
    public static final String OBJECT_NAME = "manifest.json";
    public static final String CANONICAL_STEM = "data";

    public FileDatasetShapefileManifest {
        if (version != CURRENT_VERSION || !FORMAT.equals(format)) {
            throw new IllegalArgumentException("SHP 组件清单版本或格式无效");
        }
        if (sourceName == null || sourceName.isBlank()) {
            throw new IllegalArgumentException("SHP 来源名称不能为空");
        }
        sourceName = sourceName.trim();
        components = List.copyOf(components);
        EnumSet<ShapefileComponent> kinds = EnumSet.noneOf(ShapefileComponent.class);
        for (ComponentEntry component : components) {
            if (!kinds.add(component.kind())) {
                throw new IllegalArgumentException("SHP 组件清单包含重复组件");
            }
        }
        for (ShapefileComponent component : ShapefileComponent.values()) {
            if (component.required() && !kinds.contains(component)) {
                throw new IllegalArgumentException("SHP 组件清单缺少必需组件：" + component);
            }
        }
    }

    public EnumSet<ShapefileComponent> componentKinds() {
        EnumSet<ShapefileComponent> result = EnumSet.noneOf(ShapefileComponent.class);
        components.forEach(component -> result.add(component.kind()));
        return result;
    }

    public record ComponentEntry(ShapefileComponent kind, String objectName, long sizeBytes) {
        public ComponentEntry {
            if (kind == null) {
                throw new IllegalArgumentException("SHP 组件类型不能为空");
            }
            String expectedObjectName = CANONICAL_STEM + "." + kind.extension();
            if (!expectedObjectName.equals(objectName)) {
                throw new IllegalArgumentException("SHP 组件对象名称无效");
            }
            if (sizeBytes < 0) {
                throw new IllegalArgumentException("SHP 组件大小不能小于零");
            }
        }
    }
}
