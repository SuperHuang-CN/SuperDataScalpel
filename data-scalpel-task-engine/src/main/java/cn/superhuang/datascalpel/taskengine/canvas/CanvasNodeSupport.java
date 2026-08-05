package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class CanvasNodeSupport {

    private CanvasNodeSupport() {
    }

    static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    static void required(
            String value,
            String message,
            String path,
            CanvasNodeIssueSink issues
    ) {
        if (blank(value)) {
            issues.error("REQUIRED_CONFIGURATION", message, path);
        }
    }

    static UUID parseUuid(String value, String path, CanvasNodeIssueSink issues) {
        return parseUuid(
                value,
                "REQUIRED_CONFIGURATION",
                "数据源 ID 不能为空",
                "INVALID_DATA_SOURCE_ID",
                "数据源 ID 必须是 UUID",
                path,
                issues
        );
    }

    static UUID parseModelUuid(String value, String path, CanvasNodeIssueSink issues) {
        return parseUuid(
                value,
                "MODEL_ID_REQUIRED",
                "请选择模型",
                "INVALID_MODEL_ID",
                "模型 ID 必须是 UUID",
                path,
                issues
        );
    }

    private static UUID parseUuid(
            String value,
            String requiredCode,
            String requiredMessage,
            String invalidCode,
            String invalidMessage,
            String path,
            CanvasNodeIssueSink issues
    ) {
        if (blank(value)) {
            issues.error(requiredCode, requiredMessage, path);
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            issues.error(invalidCode, invalidMessage, path);
            return null;
        }
    }

    static Map<String, CanvasColumnSchema> columns(CanvasTableSchema schema) {
        Map<String, CanvasColumnSchema> columns = new LinkedHashMap<>();
        for (CanvasColumnSchema column : schema.columns()) {
            columns.put(column.name(), column);
        }
        return columns;
    }

    static List<CanvasTableSchema> schemas(Map<String, SparkCanvasTable> tables) {
        return tables.values().stream().map(SparkCanvasTable::schema).toList();
    }

    static List<CanvasColumnSchema> concatenatedColumns(
            CanvasTableSchema left,
            CanvasTableSchema right
    ) {
        List<CanvasColumnSchema> columns = new ArrayList<>(left.columns());
        columns.addAll(right.columns());
        return List.copyOf(columns);
    }

    static void validateSupportedGeometry(
            List<CanvasColumnSchema> columns,
            String path,
            CanvasNodeIssueSink issues
    ) {
        for (CanvasColumnSchema column : columns) {
            if (column.fieldType() != PlatformDataType.GEOMETRY) {
                continue;
            }
            String columnPath = path + "." + column.name();
            if (column.geometry() == null) {
                issues.error(
                        "GEOMETRY_TYPE_DEFINITION_REQUIRED",
                        "Geometry 字段缺少空间类型定义：" + column.name(),
                        columnPath
                );
                continue;
            }
            if (!"EPSG".equals(column.geometry().crs().authority())) {
                issues.error(
                        "UNSUPPORTED_GEOMETRY_CRS",
                        "Geometry 字段第一阶段只支持 EPSG CRS：" + column.name(),
                        columnPath
                );
            }
            if (column.geometry().dimension() != CoordinateDimension.XY) {
                issues.error(
                        "UNSUPPORTED_GEOMETRY_DIMENSION",
                        "Geometry 字段第一阶段只支持 XY 维度：" + column.name(),
                        columnPath
                );
            }
        }
    }

    static String quoteIdentifier(String value) {
        return "`" + value.replace("`", "``") + "`";
    }
}
