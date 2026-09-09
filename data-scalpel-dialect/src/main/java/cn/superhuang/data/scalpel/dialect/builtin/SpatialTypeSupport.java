package cn.superhuang.data.scalpel.dialect.builtin;

import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableColumnDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableColumnType;
import cn.superhuang.data.scalpel.dialect.model.TableDefinition;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Shared, database-neutral validation used by the two V1 spatial dialects. */
final class SpatialTypeSupport {

    private SpatialTypeSupport() {
    }

    static boolean containsGeometry(TableDefinition definition) {
        return definition.columns().stream().anyMatch(column -> column.type() == TableColumnType.GEOMETRY);
    }

    static boolean isConstraintOnlyChange(TableDefinition before, TableDefinition target) {
        if (!before.table().equals(target.table())
                || !before.storage().equals(target.storage())
                || before.columns().size() != target.columns().size()) {
            return false;
        }
        Map<UUID, TableColumnDefinition> targetById = new HashMap<>();
        Map<String, TableColumnDefinition> targetByName = new HashMap<>();
        for (TableColumnDefinition column : target.columns()) {
            if (column.columnId() != null) {
                targetById.put(column.columnId(), column);
            }
            targetByName.put(column.name().toLowerCase(Locale.ROOT), column);
        }
        for (TableColumnDefinition source : before.columns()) {
            TableColumnDefinition destination = source.columnId() == null
                    ? targetByName.get(source.name().toLowerCase(Locale.ROOT))
                    : targetById.get(source.columnId());
            if (destination == null
                    || !source.name().equalsIgnoreCase(destination.name())
                    || source.type() != destination.type()
                    || !Objects.equals(source.length(), destination.length())
                    || !Objects.equals(source.precision(), destination.precision())
                    || !Objects.equals(source.scale(), destination.scale())
                    || !Objects.equals(source.columnId(), destination.columnId())
                    || !Objects.equals(source.geometry(), destination.geometry())) {
                return false;
            }
        }
        return true;
    }

    static GeometryKind geometryKind(String nativeKind) {
        if (nativeKind == null || nativeKind.isBlank()) {
            return null;
        }
        String normalized = nativeKind.trim().toUpperCase(Locale.ROOT)
                .replace(" ", "")
                .replace("_", "");
        if (normalized.endsWith("ZM")) {
            normalized = normalized.substring(0, normalized.length() - 2);
        } else if (normalized.endsWith("Z") || normalized.endsWith("M")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return switch (normalized) {
            case "GEOMETRY" -> GeometryKind.GEOMETRY;
            case "POINT" -> GeometryKind.POINT;
            case "LINESTRING" -> GeometryKind.LINESTRING;
            case "POLYGON" -> GeometryKind.POLYGON;
            case "MULTIPOINT" -> GeometryKind.MULTIPOINT;
            case "MULTILINESTRING" -> GeometryKind.MULTILINESTRING;
            case "MULTIPOLYGON" -> GeometryKind.MULTIPOLYGON;
            case "GEOMETRYCOLLECTION" -> GeometryKind.GEOMETRYCOLLECTION;
            default -> null;
        };
    }

    static String validateV1Geometry(GeometryTypeDefinition geometry) {
        if (geometry == null) {
            return "缺少 Geometry 类型定义";
        }
        if (!"EPSG".equals(geometry.crs().authority())) {
            return "空间字段第一版只支持 EPSG CRS";
        }
        if (geometry.dimension() != CoordinateDimension.XY) {
            return "空间字段第一版只支持 XY 二维坐标";
        }
        return null;
    }

    static CoordinateDimension coordinateDimension(Integer dimension) {
        if (dimension == null) {
            return null;
        }
        return switch (dimension) {
            case 2 -> CoordinateDimension.XY;
            case 3 -> CoordinateDimension.XYZ;
            case 4 -> CoordinateDimension.XYZM;
            default -> null;
        };
    }
}
