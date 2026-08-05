package cn.superhuang.data.scalpel.business.filedataset.service.parse;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFormat;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.dialect.model.LogicalType;
import cn.superhuang.data.scalpel.filegdb.FileGdbFeatureCursor;
import cn.superhuang.data.scalpel.filegdb.FileGdbReadOptions;
import cn.superhuang.data.scalpel.filegdb.FileGeodatabase;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbEnvelope;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbFeature;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbField;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbFieldType;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbLayer;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbSchema;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbSpatialReference;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbCoordinateSequence;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbGeometry;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbMultiPoint;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbPoint;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbPolygon;
import cn.superhuang.data.scalpel.filegdb.model.geometry.FileGdbPolyline;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Samples one logical layer from an unpacked, immutable FileGDB prefix. */
@Component
public class GdbFileDatasetParser implements FileDatasetParser {

    @Override
    public boolean supports(FileDatasetFormat format) {
        return format == FileDatasetFormat.GDB;
    }

    @Override
    public FileDatasetParserInputMode inputMode() {
        return FileDatasetParserInputMode.FILE_GDB;
    }

    @Override
    public ParseResult parse(
            FileDatasetParseSource source,
            FileDatasetParsingConfiguration configuration,
            int recordLimit
    ) {
        FileGeodatabase database = FileDatasetParseSource.requireFileGdb(source);
        if (!(configuration instanceof FileDatasetParsingConfiguration.Gdb gdbConfiguration)) {
            throw new FileDatasetParsingException("GDB 解析参数类型不匹配");
        }
        String layerId = gdbConfiguration.layerId();
        FileGdbLayer layer = database.layers().stream()
                .filter(candidate -> candidate.id().equals(layerId))
                .findFirst()
                .orElseThrow(() -> new FileDatasetParsingException("GDB 图层不存在或已经变化：" + layerId));
        FileGdbSchema schema = database.schema(layerId);
        boolean hasGeometry = schema.fields().stream().anyMatch(field -> field.type() == FileGdbFieldType.SHAPE);
        FileGdbSpatialReference nativeSpatialReference = schema.spatialReference();
        CrsReference crs = hasGeometry
                ? FileDatasetCrsResolver.requireEpsg(
                        nativeSpatialReference == null ? null : nativeSpatialReference.wkt(),
                        gdbConfiguration.epsgCode(),
                        "GDB 图层 " + layer.name()
                )
                : null;
        List<Field> fields = fields(schema, crs);
        Map<String, Object> metadata = sourceMetadata(layer, schema, crs);
        if (!layer.type().isCursorReadable()) {
            return new ParseResult(fields, List.of(), false, false, metadata);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        boolean truncated = false;
        try (FileGdbFeatureCursor cursor = database.openCursor(
                layerId, FileGdbReadOptions.limit(Math.addExact(recordLimit, 1)))) {
            while (cursor.hasNext()) {
                FileGdbFeature feature = cursor.next();
                if (rows.size() == recordLimit) {
                    truncated = true;
                    break;
                }
                rows.add(row(schema, feature));
            }
        }
        return new ParseResult(fields, rows, truncated, true, metadata);
    }

    private static List<Field> fields(FileGdbSchema schema, CrsReference crs) {
        List<Field> fields = new ArrayList<>(schema.fields().size());
        for (int index = 0; index < schema.fields().size(); index++) {
            FileGdbField field = schema.fields().get(index);
            fields.add(new Field(field.name(), index, typeDefinition(field, schema, crs), field.nullable()));
        }
        return List.copyOf(fields);
    }

    private static Map<String, Object> row(FileGdbSchema schema, FileGdbFeature feature) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        for (FileGdbField field : schema.fields()) {
            row.put(
                    field.name(),
                    field.type() == FileGdbFieldType.SHAPE
                            ? geometry(feature.geometry()) : feature.attribute(field.name())
            );
        }
        return row;
    }

    private static LogicalType logicalType(FileGdbFieldType type) {
        return switch (type) {
            case INT16, INT32, OID -> LogicalType.INTEGER;
            case FLOAT32, FLOAT64 -> LogicalType.DECIMAL;
            case STRING, UUID, GUID, XML -> LogicalType.STRING;
            case TIMESTAMP -> LogicalType.DATETIME;
            case BINARY -> LogicalType.BINARY;
            case SHAPE -> LogicalType.JSON;
        };
    }

    private static PlatformTypeDefinition typeDefinition(
            FileGdbField field,
            FileGdbSchema schema,
            CrsReference crs
    ) {
        return switch (field.type()) {
            case INT16, INT32, OID -> PlatformTypeDefinition.of(PlatformDataType.LONG);
            case FLOAT32 -> PlatformTypeDefinition.of(PlatformDataType.FLOAT);
            case FLOAT64 -> PlatformTypeDefinition.of(PlatformDataType.DOUBLE);
            case STRING, UUID, GUID, XML -> PlatformTypeDefinition.string(
                    field.length() > 0 && field.length() <= Integer.MAX_VALUE ? (int) field.length() : null
            );
            case TIMESTAMP -> PlatformTypeDefinition.of(PlatformDataType.TIMESTAMP_NTZ);
            case BINARY -> PlatformTypeDefinition.of(PlatformDataType.BINARY);
            case SHAPE -> PlatformTypeDefinition.geometry(new GeometryTypeDefinition(
                    geometryKind(schema),
                    requireCrs(crs),
                    coordinateDimension(schema.spatialReference())
            ));
        };
    }

    private static Map<String, Object> sourceMetadata(
            FileGdbLayer layer,
            FileGdbSchema schema,
            CrsReference crs
    ) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("layerId", layer.id());
        metadata.put("layerName", layer.name());
        metadata.put("layerType", layer.type().name());
        metadata.put("previewSupported", layer.type().isCursorReadable());
        FileGdbSpatialReference spatialReference = schema.spatialReference();
        if (crs != null) {
            LinkedHashMap<String, Object> spatial = new LinkedHashMap<>();
            if (spatialReference != null) {
                spatial.put("wkt", spatialReference.wkt());
                spatial.put("hasZ", spatialReference.hasZ());
                spatial.put("hasM", spatialReference.hasM());
                spatial.put("extent", envelope(spatialReference.extent()));
                spatial.put("zMin", spatialReference.zMin());
                spatial.put("zMax", spatialReference.zMax());
                spatial.put("mMin", spatialReference.mMin());
                spatial.put("mMax", spatialReference.mMax());
            }
            spatial.put("crsAuthority", crs.authority());
            spatial.put("crsCode", crs.code());
            spatial.put("coordinateDimension", coordinateDimension(spatialReference).name());
            spatial.put("geometryKind", geometryKind(schema).name());
            metadata.put("spatialReference", spatial);
        }
        return metadata;
    }

    private static CrsReference requireCrs(CrsReference crs) {
        if (crs == null) {
            throw new FileDatasetParsingException("GDB Shape 字段缺少空间参考");
        }
        return crs;
    }

    private static GeometryKind geometryKind(FileGdbSchema schema) {
        return switch (schema.layerType()) {
            case POINT -> GeometryKind.POINT;
            case MULTIPOINT -> GeometryKind.MULTIPOINT;
            case POLYLINE -> GeometryKind.MULTILINESTRING;
            case POLYGON -> GeometryKind.MULTIPOLYGON;
            default -> GeometryKind.GEOMETRY;
        };
    }

    private static CoordinateDimension coordinateDimension(FileGdbSpatialReference spatialReference) {
        if (spatialReference == null) {
            return CoordinateDimension.XY;
        }
        if (spatialReference.hasZ() && spatialReference.hasM()) {
            return CoordinateDimension.XYZM;
        }
        if (spatialReference.hasZ()) {
            return CoordinateDimension.XYZ;
        }
        return spatialReference.hasM() ? CoordinateDimension.XYM : CoordinateDimension.XY;
    }

    private static Map<String, Object> geometry(FileGdbGeometry geometry) {
        if (geometry == null) {
            return null;
        }
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("hasZ", geometry.hasZ());
        value.put("hasM", geometry.hasM());
        switch (geometry) {
            case FileGdbPoint point -> {
                value.put("type", "Point");
                value.put("coordinates", coordinate(point.x(), point.y(), point.z(), point.m()));
            }
            case FileGdbMultiPoint multiPoint -> {
                value.put("type", "MultiPoint");
                value.put("extent", envelope(multiPoint.envelope()));
                value.put("coordinates", coordinates(multiPoint.coordinates(), 0, multiPoint.coordinates().size()));
            }
            case FileGdbPolyline polyline -> {
                value.put("type", "Polyline");
                value.put("extent", envelope(polyline.envelope()));
                value.put("paths", parts(polyline.coordinates(), polyline.pathPointCounts()));
            }
            case FileGdbPolygon polygon -> {
                value.put("type", "Polygon");
                value.put("extent", envelope(polygon.envelope()));
                value.put("rings", parts(polygon.coordinates(), polygon.ringPointCounts()));
            }
        }
        return value;
    }

    private static List<List<List<Double>>> parts(FileGdbCoordinateSequence sequence, int[] pointCounts) {
        List<List<List<Double>>> parts = new ArrayList<>(pointCounts.length);
        int offset = 0;
        for (int pointCount : pointCounts) {
            parts.add(coordinates(sequence, offset, pointCount));
            offset += pointCount;
        }
        return parts;
    }

    private static List<List<Double>> coordinates(FileGdbCoordinateSequence sequence, int offset, int count) {
        List<List<Double>> coordinates = new ArrayList<>(count);
        for (int index = offset; index < offset + count; index++) {
            coordinates.add(coordinate(
                    sequence.x(index), sequence.y(index),
                    sequence.hasZ() ? sequence.z(index) : null,
                    sequence.hasM() ? sequence.m(index) : null
            ));
        }
        return coordinates;
    }

    private static List<Double> coordinate(double x, double y, Double z, Double m) {
        List<Double> coordinate = new ArrayList<>(4);
        coordinate.add(x);
        coordinate.add(y);
        if (z != null) {
            coordinate.add(z);
        }
        if (m != null) {
            coordinate.add(m);
        }
        return coordinate;
    }

    private static Map<String, Double> envelope(FileGdbEnvelope envelope) {
        return Map.of(
                "xMin", envelope.xMin(),
                "yMin", envelope.yMin(),
                "xMax", envelope.xMax(),
                "yMax", envelope.yMax()
        );
    }
}
