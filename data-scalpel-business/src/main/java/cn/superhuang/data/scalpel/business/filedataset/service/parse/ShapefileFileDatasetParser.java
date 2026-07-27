package cn.superhuang.data.scalpel.business.filedataset.service.parse;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFormat;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import cn.superhuang.data.scalpel.dialect.model.LogicalType;
import cn.superhuang.data.scalpel.shapefile.ShapefileDataset;
import cn.superhuang.data.scalpel.shapefile.ShapefileErrorCode;
import cn.superhuang.data.scalpel.shapefile.ShapefileException;
import cn.superhuang.data.scalpel.shapefile.ShapefileFeatureCursor;
import cn.superhuang.data.scalpel.shapefile.ShapefileReadOptions;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileEnvelope;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileFeature;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileField;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileFieldType;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileSchema;
import cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefileCoordinateSequence;
import cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefileGeometry;
import cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefileMultiPoint;
import cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefilePoint;
import cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefilePolygon;
import cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefilePolyline;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Samples attributes and geometry from one immutable Shapefile component set. */
@Component
public class ShapefileFileDatasetParser implements FileDatasetParser {

    private static final String GEOMETRY_FIELD_BASE = "_geometry";
    private static final String PREVIEW_LIMIT_REASON = "空间几何超过预览安全上限，仅保留 Schema";

    private final long maxPreviewTotalGeometryPoints;

    public ShapefileFileDatasetParser(
            @Value("${data-scalpel.file-parsing.shp.max-preview-total-geometry-points:200000}")
            long maxPreviewTotalGeometryPoints
    ) {
        if (maxPreviewTotalGeometryPoints < 1) {
            throw new IllegalArgumentException("SHP 预览累计点数上限必须大于零");
        }
        this.maxPreviewTotalGeometryPoints = maxPreviewTotalGeometryPoints;
    }

    @Override
    public boolean supports(FileDatasetFormat format) {
        return format == FileDatasetFormat.SHP;
    }

    @Override
    public FileDatasetParserInputMode inputMode() {
        return FileDatasetParserInputMode.SHAPEFILE_COMPONENT_SET;
    }

    @Override
    public ParseResult parse(
            FileDatasetParseSource source,
            FileDatasetParsingConfiguration configuration,
            int recordLimit
    ) {
        if (!(configuration instanceof FileDatasetParsingConfiguration.Shp)) {
            throw new FileDatasetParsingException("SHP 解析参数类型不匹配");
        }
        ShapefileDataset dataset = FileDatasetParseSource.requireShapefile(source);
        ShapefileSchema schema = dataset.schema();
        String geometryField = geometryField(schema.fields());
        List<Field> fields = fields(schema, geometryField);
        List<Map<String, Object>> rows = new ArrayList<>();
        boolean truncated = false;
        long sampledGeometryPointCount = 0;
        try {
            try (ShapefileFeatureCursor cursor = dataset.openCursor(
                    ShapefileReadOptions.limit(Math.addExact(recordLimit, 1)))) {
                while (cursor.hasNext()) {
                    ShapefileFeature feature = cursor.next();
                    if (rows.size() == recordLimit) {
                        truncated = true;
                        break;
                    }
                    long featurePointCount = geometryPointCount(feature.geometry());
                    if (featurePointCount > maxPreviewTotalGeometryPoints - sampledGeometryPointCount) {
                        return schemaOnlyResult(schema, geometryField, fields);
                    }
                    sampledGeometryPointCount += featurePointCount;
                    rows.add(row(schema, feature, geometryField));
                }
            }
        } catch (ShapefileException exception) {
            if (exception.errorCode() == ShapefileErrorCode.LIMIT_EXCEEDED) {
                return schemaOnlyResult(schema, geometryField, fields);
            }
            throw exception;
        }
        Map<String, Object> metadata = sourceMetadata(schema, geometryField, true);
        metadata.put("sampledGeometryPointCount", sampledGeometryPointCount);
        return new ParseResult(fields, rows, truncated, true, metadata);
    }

    @Override
    public ParseResult validate(
            FileDatasetParseSource source,
            FileDatasetParsingConfiguration configuration,
            int previewLimit
    ) {
        if (!(configuration instanceof FileDatasetParsingConfiguration.Shp)) {
            throw new FileDatasetParsingException("SHP 解析参数类型不匹配");
        }
        ShapefileDataset dataset = FileDatasetParseSource.requireShapefile(source);
        ShapefileSchema schema = dataset.schema();
        String geometryField = geometryField(schema.fields());
        List<Field> fields = fields(schema, geometryField);
        List<Map<String, Object>> rows = new ArrayList<>();
        long scanned = 0;
        long previewGeometryPoints = 0;
        boolean previewSupported = true;
        if (schema.recordCount() > Integer.MAX_VALUE) {
            throw new FileDatasetParsingException("SHP 记录数超过当前完整校验能力");
        }
        if (schema.recordCount() > 0) {
            try (ShapefileFeatureCursor cursor = dataset.openCursor(
                    ShapefileReadOptions.limit((int) schema.recordCount()))) {
                while (cursor.hasNext()) {
                    ShapefileFeature feature = cursor.next();
                    scanned++;
                    if (rows.size() >= previewLimit || !previewSupported) {
                        continue;
                    }
                    long points = geometryPointCount(feature.geometry());
                    if (points > maxPreviewTotalGeometryPoints - previewGeometryPoints) {
                        previewSupported = false;
                        rows.clear();
                        continue;
                    }
                    previewGeometryPoints += points;
                    rows.add(row(schema, feature, geometryField));
                }
            }
        }
        Map<String, Object> metadata = sourceMetadata(schema, geometryField, previewSupported);
        metadata.put("shapeHasZ", schema.shapeType().hasZ());
        metadata.put("shapeHasM", schema.shapeType().hasM());
        metadata.put("sampledGeometryPointCount", previewGeometryPoints);
        if (!previewSupported) {
            metadata.put("previewUnavailableReason", PREVIEW_LIMIT_REASON);
            metadata.put("maxPreviewTotalGeometryPoints", maxPreviewTotalGeometryPoints);
        }
        return new ParseResult(
                fields, rows, scanned > rows.size(), previewSupported, metadata, scanned
        );
    }

    private ParseResult schemaOnlyResult(
            ShapefileSchema schema,
            String geometryField,
            List<Field> fields
    ) {
        Map<String, Object> metadata = sourceMetadata(schema, geometryField, false);
        metadata.put("previewUnavailableReason", PREVIEW_LIMIT_REASON);
        metadata.put("maxPreviewTotalGeometryPoints", maxPreviewTotalGeometryPoints);
        return new ParseResult(fields, List.of(), false, false, metadata);
    }

    private static List<Field> fields(ShapefileSchema schema, String geometryField) {
        List<Field> result = new ArrayList<>(schema.fields().size() + 1);
        for (ShapefileField field : schema.fields()) {
            result.add(new Field(
                    field.name(), field.sortOrder(), typeDefinition(field), field.nullable()
            ));
        }
        result.add(new Field(geometryField, result.size(), PlatformTypeDefinition.string(null), true));
        return List.copyOf(result);
    }

    private static Map<String, Object> row(
            ShapefileSchema schema,
            ShapefileFeature feature,
            String geometryField
    ) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (ShapefileField field : schema.fields()) {
            result.put(field.name(), feature.attribute(field.name()));
        }
        result.put(geometryField, geometry(feature.geometry()));
        return result;
    }

    private static LogicalType logicalType(ShapefileFieldType type) {
        return switch (type) {
            case STRING -> LogicalType.STRING;
            case INTEGER -> LogicalType.INTEGER;
            case DECIMAL -> LogicalType.DECIMAL;
            case BOOLEAN -> LogicalType.BOOLEAN;
            case DATE -> LogicalType.DATE;
        };
    }

    private static PlatformTypeDefinition typeDefinition(ShapefileField field) {
        return switch (field.type()) {
            case STRING -> PlatformTypeDefinition.string(field.length());
            case INTEGER -> PlatformTypeDefinition.of(PlatformDataType.LONG);
            case DECIMAL -> FileDatasetTypeDefinitions.decimal(field.length(), field.decimalCount());
            case BOOLEAN -> PlatformTypeDefinition.of(PlatformDataType.BOOLEAN);
            case DATE -> PlatformTypeDefinition.of(PlatformDataType.DATE);
        };
    }

    private static String geometryField(List<ShapefileField> fields) {
        Set<String> names = new HashSet<>();
        fields.forEach(field -> names.add(field.name()));
        String candidate = GEOMETRY_FIELD_BASE;
        int suffix = 1;
        while (!names.add(candidate)) {
            candidate = GEOMETRY_FIELD_BASE + "_" + suffix++;
        }
        return candidate;
    }

    private static Map<String, Object> sourceMetadata(
            ShapefileSchema schema,
            String geometryField,
            boolean previewSupported
    ) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("shapeType", schema.shapeType().name());
        metadata.put("shapeHasZ", schema.shapeType().hasZ());
        metadata.put("shapeHasM", schema.shapeType().hasM());
        metadata.put("recordCount", schema.recordCount());
        metadata.put("dbfCharset", schema.dbfCharset().name());
        metadata.put("dbfFields", schema.fields().stream().map(ShapefileFileDatasetParser::fieldMetadata).toList());
        metadata.put("geometryField", geometryField);
        metadata.put("previewSupported", previewSupported);
        metadata.put("extent", envelope(schema.envelope()));
        if (schema.spatialReference() != null) {
            metadata.put("spatialReference", Map.of(
                    "wkt", schema.spatialReference().wkt(),
                    "normalizedWkt", normalizeWkt(schema.spatialReference().wkt())
            ));
        }
        return metadata;
    }

    private static String normalizeWkt(String wkt) {
        return wkt == null ? "" : wkt.trim().replaceAll("\\s+", " ");
    }

    private static Map<String, Object> fieldMetadata(ShapefileField field) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", field.name());
        metadata.put("type", field.type().name());
        metadata.put("length", field.length());
        metadata.put("decimalCount", field.decimalCount());
        metadata.put("nullable", field.nullable());
        metadata.put("sortOrder", field.sortOrder());
        return metadata;
    }

    private static long geometryPointCount(ShapefileGeometry geometry) {
        if (geometry == null) {
            return 0;
        }
        return switch (geometry) {
            case ShapefilePoint ignored -> 1;
            case ShapefileMultiPoint multiPoint -> multiPoint.coordinates().size();
            case ShapefilePolyline polyline -> polyline.coordinates().size();
            case ShapefilePolygon polygon -> polygon.coordinates().size();
        };
    }

    private static Map<String, Object> geometry(ShapefileGeometry geometry) {
        if (geometry == null) {
            return null;
        }
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("hasZ", geometry.hasZ());
        value.put("hasM", geometry.hasM());
        switch (geometry) {
            case ShapefilePoint point -> {
                value.put("type", "Point");
                value.put("coordinates", coordinate(point.x(), point.y(), point.z(), point.m()));
            }
            case ShapefileMultiPoint multiPoint -> {
                value.put("type", "MultiPoint");
                value.put("extent", envelope(multiPoint.envelope()));
                value.put("coordinates", coordinates(
                        multiPoint.coordinates(), 0, multiPoint.coordinates().size()
                ));
            }
            case ShapefilePolyline polyline -> {
                value.put("type", "Polyline");
                value.put("extent", envelope(polyline.envelope()));
                value.put("paths", parts(polyline.coordinates(), polyline.partPointCounts()));
            }
            case ShapefilePolygon polygon -> {
                value.put("type", "Polygon");
                value.put("extent", envelope(polygon.envelope()));
                value.put("rings", parts(polygon.coordinates(), polygon.ringPointCounts()));
            }
        }
        return value;
    }

    private static List<List<List<Double>>> parts(ShapefileCoordinateSequence sequence, int[] pointCounts) {
        List<List<List<Double>>> result = new ArrayList<>(pointCounts.length);
        int offset = 0;
        for (int pointCount : pointCounts) {
            result.add(coordinates(sequence, offset, pointCount));
            offset += pointCount;
        }
        return result;
    }

    private static List<List<Double>> coordinates(
            ShapefileCoordinateSequence sequence,
            int offset,
            int count
    ) {
        List<List<Double>> result = new ArrayList<>(count);
        for (int index = offset; index < offset + count; index++) {
            result.add(coordinate(
                    sequence.x(index),
                    sequence.y(index),
                    sequence.hasZ() ? sequence.z(index) : null,
                    sequence.hasM() ? sequence.m(index) : null
            ));
        }
        return result;
    }

    private static List<Double> coordinate(double x, double y, Double z, Double m) {
        List<Double> result = new ArrayList<>(4);
        result.add(finiteOrNull(x));
        result.add(finiteOrNull(y));
        if (z != null) {
            result.add(finiteOrNull(z));
        }
        if (m != null) {
            result.add(finiteOrNull(m));
        }
        return result;
    }

    private static Map<String, Object> envelope(ShapefileEnvelope envelope) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("xMin", finiteOrNull(envelope.xmin()));
        value.put("yMin", finiteOrNull(envelope.ymin()));
        value.put("xMax", finiteOrNull(envelope.xmax()));
        value.put("yMax", finiteOrNull(envelope.ymax()));
        return value;
    }

    private static Double finiteOrNull(double value) {
        return Double.isFinite(value) ? value : null;
    }
}
