package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.ConnectionKind;
import cn.superhuang.data.scalpel.contract.task.DataSourcePurpose;
import cn.superhuang.data.scalpel.contract.task.FileOutputConfiguration;
import cn.superhuang.data.scalpel.contract.task.FileOutputFormatOptions;
import cn.superhuang.data.scalpel.contract.task.FileOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ShapefileAttributeMapping;
import cn.superhuang.data.scalpel.contract.task.ShapefileShapeType;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.geotools.api.referencing.FactoryException;
import org.geotools.referencing.CRS;

import java.util.Map;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class FileOutputNodeOperator implements CanvasNodeOperator {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.FILE_OUTPUT;
    }

    @Override
    public CanvasNodeCategory category() {
        return CanvasNodeCategory.OUTPUT;
    }

    @Override
    public Set<CanvasExecutionMode> supportedModes() {
        return Set.of(CanvasExecutionMode.BATCH);
    }

    @Override
    public CanvasNodeOperationResult apply(
            CanvasNodeDefinition definition,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeOperationContext context
    ) {
        if (!(definition instanceof FileOutputNodeDefinition node)) {
            throw new IllegalArgumentException("FILE_OUTPUT operator received " + definition.nodeType());
        }
        FileOutputConfiguration configuration = node.configuration();
        if (configuration == null) return CanvasNodeOperationResult.outputOnly();

        CanvasNodeIssueSink issues = context.issues();
        CanvasNodeSupport.required(
                configuration.sourceTableName(), "请选择来源表",
                "configuration.sourceTableName", issues);
        UUID dataSourceId = CanvasNodeSupport.parseUuid(
                configuration.dataSourceId(), "configuration.dataSourceId", issues);
        validateTargetPath(configuration.targetPath(), issues);
        if (configuration.conflictPolicy() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择目录冲突策略",
                    "configuration.conflictPolicy");
        }
        validateFormat(configuration.formatOptions(), issues);

        SparkCanvasTable source = inputs.get(configuration.sourceTableName());
        if (!CanvasNodeSupport.blank(configuration.sourceTableName()) && source == null) {
            issues.error("TABLE_NOT_FOUND", "来源表不在上游数据中：" + configuration.sourceTableName(),
                    "configuration.sourceTableName");
        } else if (source != null) {
            if (configuration.formatOptions() instanceof FileOutputFormatOptions.Shapefile shapefile) {
                validateShapefile(shapefile, source, issues);
            } else if (configuration.formatOptions() instanceof FileOutputFormatOptions.GeoParquet geoParquet) {
                validateGeoParquet(geoParquet, source, issues);
            } else if (configuration.formatOptions() instanceof FileOutputFormatOptions.GeoJson geoJson) {
                validateGeoJson(geoJson, source, issues);
            } else if (source.schema().columns().stream().anyMatch(column ->
                    column.fieldType() == PlatformDataType.GEOMETRY)) {
                issues.error(
                        "GEOMETRY_FIELD_OPERATION_UNSUPPORTED",
                        "CSV、JSON Lines 和普通 Parquet 不支持 Geometry 字段，请先序列化并移除原字段",
                        "configuration.sourceTableName"
                );
            }
        }
        MetadataIndex.DataSourceEntry dataSource =
                dataSourceId == null ? null : context.metadataIndex().dataSource(dataSourceId);
        if (dataSourceId != null && (dataSource == null
                || !dataSource.metadata().enabled()
                || dataSource.metadata().connectionKind() != ConnectionKind.S3
                || !dataSource.metadata().purposes().contains(DataSourcePurpose.DISTRIBUTION))) {
            issues.error(
                    "DATA_SOURCE_UNAVAILABLE",
                    "目标数据源不存在、未启用、不是 S3 或不具有 DISTRIBUTION 用途",
                    "configuration.dataSourceId"
            );
        }
        if (source == null || issues.hasErrors()) return CanvasNodeOperationResult.outputOnly();
        return CanvasNodeOperationResult.fileOutput(
                context.dataAccess().prepareFileOutput(node, source.schema(), source.dataset()));
    }

    private static void validateTargetPath(String path, CanvasNodeIssueSink issues) {
        CanvasNodeSupport.required(path, "请输入目标目录", "configuration.targetPath", issues);
        if (CanvasNodeSupport.blank(path)) {
            return;
        }
        if (path.length() > 1024
                || path.startsWith("/")
                || path.contains("\\")
                || path.contains("://")
                || path.contains("?")
                || path.contains("#")) {
            issues.error("INVALID_FILE_OUTPUT_PATH", "目标目录必须是合法的 S3 相对路径",
                    "configuration.targetPath");
            return;
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)
                    || "_temporary".equalsIgnoreCase(segment)) {
                issues.error("INVALID_FILE_OUTPUT_PATH",
                        "目标目录不能包含空段、.、.. 或 _temporary",
                        "configuration.targetPath");
                return;
            }
        }
    }

    private static void validateFormat(
            FileOutputFormatOptions options,
            CanvasNodeIssueSink issues
    ) {
        if (options == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择文件格式",
                    "configuration.formatOptions");
            return;
        }
        if (options instanceof FileOutputFormatOptions.Csv csv) {
            singleCharacter(csv.delimiter(), "分隔符", "delimiter", issues);
            singleCharacter(csv.quote(), "引用符", "quote", issues);
            singleCharacter(csv.escape(), "转义符", "escape", issues);
            if (csv.nullValue() == null) {
                issues.error("REQUIRED_CONFIGURATION", "CSV 空值文本不能为空",
                        "configuration.formatOptions.nullValue");
            }
        } else if (options instanceof FileOutputFormatOptions.Shapefile shapefile) {
            String basePath = "configuration.formatOptions";
            if (CanvasNodeSupport.blank(shapefile.baseName())) {
                issues.error("SHAPEFILE_INVALID_BASE_NAME", "请输入 Shapefile 文件基础名",
                        basePath + ".baseName");
            } else if (shapefile.baseName().codePointCount(0, shapefile.baseName().length()) > 64
                    || !shapefile.baseName().matches("[\\p{L}\\p{N}][\\p{L}\\p{N}_-]{0,63}")) {
                issues.error("SHAPEFILE_INVALID_BASE_NAME",
                        "基础名只能包含中文、字母、数字、_、-，长度不能超过 64",
                        basePath + ".baseName");
            }
            if (shapefile.packageMode() == null) {
                issues.error("REQUIRED_CONFIGURATION", "请选择 Shapefile 输出形态",
                        basePath + ".packageMode");
            }
            CanvasNodeSupport.required(
                    shapefile.geometryColumnName(), "请选择 Geometry 字段",
                    basePath + ".geometryColumnName", issues);
            if (shapefile.targetShapeType() == null) {
                issues.error("REQUIRED_CONFIGURATION", "请选择目标 Shape 类型",
                        basePath + ".targetShapeType");
            }
            if (shapefile.attributeMappings() == null
                    || shapefile.attributeMappings().isEmpty()
                    || shapefile.attributeMappings().size() > 255) {
                issues.error("SHAPEFILE_ATTRIBUTE_MAPPING_INVALID",
                        "Shapefile 必须配置 1 到 255 个 DBF 属性字段",
                        basePath + ".attributeMappings");
            }
        } else if (options instanceof FileOutputFormatOptions.GeoParquet geoParquet) {
            String basePath = "configuration.formatOptions";
            CanvasNodeSupport.required(
                    geoParquet.geometryColumnName(), "请选择 Geometry 字段",
                    basePath + ".geometryColumnName", issues);
            if (geoParquet.compression() == null) {
                issues.error("GEOPARQUET_FORMAT_OPTION_INVALID", "请选择 GeoParquet 压缩方式",
                        basePath + ".compression");
            }
            if (geoParquet.coveringMode() == null) {
                issues.error("GEOPARQUET_FORMAT_OPTION_INVALID", "请选择 GeoParquet covering 模式",
                        basePath + ".coveringMode");
            }
        } else if (options instanceof FileOutputFormatOptions.GeoJson geoJson) {
            String basePath = "configuration.formatOptions";
            validateBaseName(
                    geoJson.baseName(), "GeoJSON", "GEOJSON_INVALID_BASE_NAME",
                    basePath + ".baseName", issues);
            CanvasNodeSupport.required(
                    geoJson.geometryColumnName(), "请选择 Geometry 字段",
                    basePath + ".geometryColumnName", issues);
            if (geoJson.idColumnName() != null && CanvasNodeSupport.blank(geoJson.idColumnName())) {
                issues.error("GEOJSON_ID_FIELD_INVALID", "Feature ID 字段必须为 null 或有效字段名",
                        basePath + ".idColumnName");
            }
        }
    }

    private static void validateGeoParquet(
            FileOutputFormatOptions.GeoParquet options,
            SparkCanvasTable source,
            CanvasNodeIssueSink issues
    ) {
        String basePath = "configuration.formatOptions";
        if (source.schema().datasetKind() != CanvasDatasetKind.BOUNDED) {
            issues.error("GEOPARQUET_UNBOUNDED_SOURCE_UNSUPPORTED",
                    "GeoParquet 只支持有界批数据", "configuration.sourceTableName");
        }
        List<CanvasColumnSchema> geometryColumns = source.schema().columns().stream()
                .filter(column -> column.fieldType() == PlatformDataType.GEOMETRY)
                .toList();
        if (geometryColumns.isEmpty()) {
            issues.error("GEOPARQUET_GEOMETRY_REQUIRED", "GeoParquet 来源必须包含一个 Geometry 字段",
                    basePath + ".geometryColumnName");
        } else if (geometryColumns.size() > 1) {
            issues.error("GEOPARQUET_MULTIPLE_GEOMETRY_COLUMNS_UNSUPPORTED",
                    "GeoParquet 首版只支持一个 Geometry 字段，请先使用 SELECT_COLUMNS",
                    "configuration.sourceTableName");
        }
        CanvasColumnSchema geometry = CanvasNodeSupport.columns(source.schema())
                .get(options.geometryColumnName());
        if (!CanvasNodeSupport.blank(options.geometryColumnName())) {
            if (geometry == null || geometry.fieldType() != PlatformDataType.GEOMETRY
                    || geometry.geometry() == null) {
                issues.error("GEOPARQUET_GEOMETRY_REQUIRED",
                        "所选字段不存在或不是 Geometry：" + options.geometryColumnName(),
                        basePath + ".geometryColumnName");
            } else {
                if (!"EPSG".equals(geometry.geometry().crs().authority())) {
                    issues.error("GEOPARQUET_CRS_UNSUPPORTED", "GeoParquet 首版只支持 EPSG CRS",
                            basePath + ".geometryColumnName");
                }
                if (geometry.geometry().dimension() != CoordinateDimension.XY) {
                    issues.error("GEOPARQUET_DIMENSION_UNSUPPORTED", "GeoParquet 首版只支持 XY 维度",
                            basePath + ".geometryColumnName");
                }
                if ("EPSG".equals(geometry.geometry().crs().authority())) {
                    try {
                        GeoParquetCrsSupport.projJson(geometry.geometry().crs().code());
                    } catch (IllegalArgumentException exception) {
                        issues.error("GEOPARQUET_CRS_UNSUPPORTED", exception.getMessage(),
                                basePath + ".geometryColumnName");
                    }
                }
                if (geometry.geometry().kind() == GeometryKind.GEOMETRY) {
                    issues.warning("GEOPARQUET_RUNTIME_GEOMETRY_TYPE_CHECK",
                            "通用 Geometry 将在运行时逐行校验实际空间值",
                            basePath + ".geometryColumnName");
                }
            }
        }
        if (options.coveringMode()
                == cn.superhuang.data.scalpel.contract.task.GeoParquetCoveringMode.ROW_BBOX
                && !CanvasNodeSupport.blank(options.geometryColumnName())) {
            String coveringColumnName = options.geometryColumnName() + "_bbox";
            if (source.schema().columns().stream()
                    .anyMatch(column -> column.name().equals(coveringColumnName))) {
                issues.error("GEOPARQUET_COVERING_COLUMN_CONFLICT",
                        "逐行 bbox 字段与来源字段重名：" + coveringColumnName,
                        basePath + ".coveringMode");
            }
        }
    }

    private static void validateGeoJson(
            FileOutputFormatOptions.GeoJson options,
            SparkCanvasTable source,
            CanvasNodeIssueSink issues
    ) {
        String basePath = "configuration.formatOptions";
        if (source.schema().datasetKind() != CanvasDatasetKind.BOUNDED) {
            issues.error("GEOJSON_UNBOUNDED_SOURCE_UNSUPPORTED",
                    "GeoJSON 只支持有界批数据", "configuration.sourceTableName");
        }
        List<CanvasColumnSchema> geometryColumns = source.schema().columns().stream()
                .filter(column -> column.fieldType() == PlatformDataType.GEOMETRY)
                .toList();
        if (geometryColumns.isEmpty()) {
            issues.error("GEOJSON_GEOMETRY_REQUIRED", "GeoJSON 来源必须包含一个 Geometry 字段",
                    basePath + ".geometryColumnName");
        } else if (geometryColumns.size() > 1) {
            issues.error("GEOJSON_MULTIPLE_GEOMETRY_COLUMNS_UNSUPPORTED",
                    "GeoJSON 首版只支持一个 Geometry 字段，请先使用 SELECT_COLUMNS",
                    "configuration.sourceTableName");
        }
        Map<String, CanvasColumnSchema> columns = CanvasNodeSupport.columns(source.schema());
        CanvasColumnSchema geometry = columns.get(options.geometryColumnName());
        if (!CanvasNodeSupport.blank(options.geometryColumnName())) {
            if (geometry == null || geometry.fieldType() != PlatformDataType.GEOMETRY
                    || geometry.geometry() == null) {
                issues.error("GEOJSON_GEOMETRY_REQUIRED",
                        "所选字段不存在或不是 Geometry：" + options.geometryColumnName(),
                        basePath + ".geometryColumnName");
            } else {
                if (!"EPSG".equals(geometry.geometry().crs().authority())
                        || geometry.geometry().crs().code() != 4326) {
                    issues.error("GEOJSON_CRS_REQUIRES_WGS84",
                            "RFC 7946 GeoJSON 只支持 EPSG:4326，请先使用 SPATIAL_TRANSFORM",
                            basePath + ".geometryColumnName");
                }
                if (geometry.geometry().dimension() != CoordinateDimension.XY) {
                    issues.error("GEOJSON_DIMENSION_UNSUPPORTED", "GeoJSON 首版只支持 XY 维度",
                            basePath + ".geometryColumnName");
                }
                if (geometry.geometry().kind() == GeometryKind.GEOMETRY) {
                    issues.warning("GEOJSON_RUNTIME_GEOMETRY_TYPE_CHECK",
                            "通用 Geometry 将在运行时逐行校验实际空间值",
                            basePath + ".geometryColumnName");
                }
            }
        }
        for (CanvasColumnSchema column : source.schema().columns()) {
            if (column.fieldType() == PlatformDataType.BINARY) {
                issues.error("GEOJSON_PROPERTY_TYPE_UNSUPPORTED",
                        "GeoJSON properties 不支持 BINARY 字段：" + column.name(),
                        "configuration.sourceTableName");
            }
        }
        if (options.idColumnName() != null && !CanvasNodeSupport.blank(options.idColumnName())) {
            CanvasColumnSchema idColumn = columns.get(options.idColumnName());
            if (idColumn == null || !Set.of(
                    PlatformDataType.STRING,
                    PlatformDataType.BYTE,
                    PlatformDataType.SHORT,
                    PlatformDataType.INTEGER,
                    PlatformDataType.LONG
            ).contains(idColumn.fieldType())) {
                issues.error("GEOJSON_ID_FIELD_INVALID",
                        "Feature ID 只支持 STRING 或整数字段：" + options.idColumnName(),
                        basePath + ".idColumnName");
            } else if (idColumn.fieldType() == PlatformDataType.LONG) {
                issues.warning("GEOJSON_LONG_ID_INTEROPERABILITY",
                        "LONG Feature ID 可能超出部分 JavaScript 消费端的安全整数范围",
                        basePath + ".idColumnName");
            }
        }
    }

    private static void validateBaseName(
            String value,
            String format,
            String code,
            String path,
            CanvasNodeIssueSink issues
    ) {
        if (CanvasNodeSupport.blank(value)) {
            issues.error(code, "请输入 " + format + " 文件基础名", path);
        } else if (value.codePointCount(0, value.length()) > 64
                || !value.matches("[\\p{L}\\p{N}][\\p{L}\\p{N}_-]{0,63}")) {
            issues.error(code, "基础名只能包含中文、字母、数字、_、-，长度不能超过 64", path);
        }
    }

    private static void validateShapefile(
            FileOutputFormatOptions.Shapefile options,
            SparkCanvasTable source,
            CanvasNodeIssueSink issues
    ) {
        String basePath = "configuration.formatOptions";
        if (source.schema().datasetKind() != CanvasDatasetKind.BOUNDED) {
            issues.error("SHAPEFILE_UNBOUNDED_SOURCE_UNSUPPORTED",
                    "Shapefile 只支持有界批数据",
                    "configuration.sourceTableName");
        }
        Map<String, CanvasColumnSchema> columns = CanvasNodeSupport.columns(source.schema());
        CanvasColumnSchema geometry = columns.get(options.geometryColumnName());
        if (!CanvasNodeSupport.blank(options.geometryColumnName())) {
            if (geometry == null || geometry.fieldType() != PlatformDataType.GEOMETRY
                    || geometry.geometry() == null) {
                issues.error("SHAPEFILE_GEOMETRY_REQUIRED",
                        "所选字段不存在或不是 Geometry：" + options.geometryColumnName(),
                        basePath + ".geometryColumnName");
            } else {
                if (!"EPSG".equals(geometry.geometry().crs().authority())
                        || geometry.geometry().dimension() != CoordinateDimension.XY) {
                    issues.error("SHAPEFILE_CRS_UNSUPPORTED",
                            "Shapefile 只支持 EPSG CRS 和 XY 维度",
                            basePath + ".geometryColumnName");
                } else {
                    try {
                        CRS.decode("EPSG:" + geometry.geometry().crs().code(), true);
                    } catch (FactoryException exception) {
                        issues.error("SHAPEFILE_CRS_UNSUPPORTED",
                                "GeoTools 无法解析 EPSG:" + geometry.geometry().crs().code(),
                                basePath + ".geometryColumnName");
                    }
                }
                validateShapeKind(geometry.geometry().kind(), options.targetShapeType(), issues);
                if (geometry.geometry().kind() == GeometryKind.GEOMETRY) {
                    issues.warning("SHAPEFILE_RUNTIME_GEOMETRY_TYPE_CHECK",
                            "通用 Geometry 将在运行时逐行校验目标 Shape 类型",
                            basePath + ".targetShapeType");
                }
            }
        }

        if (options.attributeMappings() == null) return;
        Set<String> sources = new HashSet<>();
        Set<String> targets = new HashSet<>();
        for (int index = 0; index < options.attributeMappings().size(); index++) {
            ShapefileAttributeMapping mapping = options.attributeMappings().get(index);
            String path = basePath + ".attributeMappings[" + index + "]";
            if (mapping == null) {
                issues.error("SHAPEFILE_ATTRIBUTE_MAPPING_INVALID", "DBF 字段映射不能为空", path);
                continue;
            }
            if (CanvasNodeSupport.blank(mapping.sourceColumnName())) {
                issues.error("SHAPEFILE_ATTRIBUTE_MAPPING_INVALID", "请选择来源字段",
                        path + ".sourceColumnName");
                continue;
            }
            if (!sources.add(mapping.sourceColumnName())) {
                issues.error("SHAPEFILE_ATTRIBUTE_MAPPING_INVALID",
                        "来源字段不能重复映射：" + mapping.sourceColumnName(),
                        path + ".sourceColumnName");
            }
            CanvasColumnSchema column = columns.get(mapping.sourceColumnName());
            if (column == null) {
                issues.error("SHAPEFILE_ATTRIBUTE_MAPPING_INVALID",
                        "来源字段不存在：" + mapping.sourceColumnName(),
                        path + ".sourceColumnName");
            }
            if (CanvasNodeSupport.blank(mapping.targetFieldName())
                    || !mapping.targetFieldName().matches("[A-Za-z_][A-Za-z0-9_]{0,9}")) {
                issues.error("SHAPEFILE_DBF_FIELD_NAME_INVALID",
                        "DBF 字段名必须是最多 10 位的 ASCII 字母、数字或下划线",
                        path + ".targetFieldName");
            } else if (!targets.add(mapping.targetFieldName().toUpperCase(Locale.ROOT))) {
                issues.error("SHAPEFILE_DBF_FIELD_NAME_DUPLICATE",
                        "DBF 字段名按大小写不敏感必须唯一：" + mapping.targetFieldName(),
                        path + ".targetFieldName");
            }
            if (column != null) validateAttributeType(column, mapping, path, issues);
        }
    }

    private static void validateShapeKind(
            GeometryKind sourceKind,
            ShapefileShapeType targetType,
            CanvasNodeIssueSink issues
    ) {
        if (targetType == null || sourceKind == GeometryKind.GEOMETRY) return;
        boolean compatible = switch (targetType) {
            case POINT -> sourceKind == GeometryKind.POINT;
            case MULTIPOINT -> sourceKind == GeometryKind.POINT
                    || sourceKind == GeometryKind.MULTIPOINT;
            case POLYLINE -> sourceKind == GeometryKind.LINESTRING
                    || sourceKind == GeometryKind.MULTILINESTRING;
            case POLYGON -> sourceKind == GeometryKind.POLYGON
                    || sourceKind == GeometryKind.MULTIPOLYGON;
        };
        if (!compatible) {
            issues.error("SHAPEFILE_GEOMETRY_KIND_INCOMPATIBLE",
                    sourceKind + " 不能写为 " + targetType,
                    "configuration.formatOptions.targetShapeType");
        }
    }

    private static void validateAttributeType(
            CanvasColumnSchema column,
            ShapefileAttributeMapping mapping,
            String path,
            CanvasNodeIssueSink issues
    ) {
        if (column.fieldType() == PlatformDataType.STRING) {
            if (mapping.targetStringByteLength() == null
                    || mapping.targetStringByteLength() < 1
                    || mapping.targetStringByteLength() > 254) {
                issues.error("SHAPEFILE_DBF_STRING_WIDTH_INVALID",
                        "STRING 字段必须配置 1 到 254 的 UTF-8 字节宽度",
                        path + ".targetStringByteLength");
            } else if (column.length() == null
                    || (long) column.length() * 4L > mapping.targetStringByteLength()) {
                issues.warning("SHAPEFILE_STRING_VALUE_MAY_OVERFLOW",
                        "来源字符串的实际 UTF-8 字节数可能超过 DBF 字段宽度",
                        path + ".targetStringByteLength");
            }
            return;
        }
        if (mapping.targetStringByteLength() != null) {
            issues.error("SHAPEFILE_DBF_STRING_WIDTH_INVALID",
                    "只有 STRING 字段可以配置 DBF 字符宽度",
                    path + ".targetStringByteLength");
        }
        if (column.fieldType() == PlatformDataType.DECIMAL) {
            if (column.precision() == null || column.scale() == null) {
                issues.error("SHAPEFILE_DBF_NUMERIC_WIDTH_UNSUPPORTED",
                        "DECIMAL 字段缺少精度或小数位：" + column.name(),
                        path + ".sourceColumnName");
                return;
            }
            int width = column.precision() + 1 + (column.scale() > 0 ? 1 : 0);
            if (width > 20) {
                issues.error("SHAPEFILE_DBF_NUMERIC_WIDTH_UNSUPPORTED",
                        "DECIMAL 字段写入 DBF 所需宽度超过 20：" + column.name(),
                        path + ".sourceColumnName");
            }
            return;
        }
        if (Set.of(
                PlatformDataType.BOOLEAN,
                PlatformDataType.BYTE,
                PlatformDataType.SHORT,
                PlatformDataType.INTEGER,
                PlatformDataType.LONG,
                PlatformDataType.DATE
        ).contains(column.fieldType())) return;
        issues.error("SHAPEFILE_ATTRIBUTE_TYPE_UNSUPPORTED",
                "DBF 不支持属性字段类型 " + column.fieldType() + "：" + column.name(),
                path + ".sourceColumnName");
    }

    private static void singleCharacter(
            String value,
            String label,
            String field,
            CanvasNodeIssueSink issues
    ) {
        if (value == null || value.codePointCount(0, value.length()) != 1
                || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            issues.error("INVALID_FILE_OUTPUT_FORMAT_OPTION",
                    label + "必须是一个非换行字符",
                    "configuration.formatOptions." + field);
        }
    }
}
