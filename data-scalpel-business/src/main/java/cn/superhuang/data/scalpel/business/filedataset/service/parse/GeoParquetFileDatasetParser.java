package cn.superhuang.data.scalpel.business.filedataset.service.parse;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFormat;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.api.ReadSupport;
import org.apache.parquet.hadoop.example.GroupReadSupport;
import org.apache.parquet.hadoop.metadata.BlockMetaData;
import org.apache.parquet.hadoop.metadata.ParquetMetadata;
import org.apache.parquet.io.InputFile;
import org.apache.parquet.io.LocalInputFile;
import org.apache.parquet.io.api.Binary;
import org.apache.parquet.format.converter.ParquetMetadataConverter;
import org.apache.parquet.schema.GroupType;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.Type;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Validates the WKB-only, XY subset of GeoParquet 1.0/1.1. */
@Component
public class GeoParquetFileDatasetParser implements FileDatasetParser {

    private static final Set<String> SUPPORTED_VERSIONS = Set.of("1.0.0", "1.1.0");
    private static final List<String> BBOX_MEMBERS = List.of("xmin", "ymin", "xmax", "ymax");

    private final ParquetFileDatasetParser parquetParser;
    private final ObjectMapper objectMapper;

    public GeoParquetFileDatasetParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.parquetParser = new ParquetFileDatasetParser(objectMapper);
    }

    @Override
    public boolean supports(FileDatasetFormat format) {
        return format == FileDatasetFormat.GEOPARQUET;
    }

    @Override
    public FileDatasetParserInputMode inputMode() {
        return FileDatasetParserInputMode.LOCAL_FILE;
    }

    @Override
    public ParseResult parse(FileDatasetParseSource source, FileDatasetParsingConfiguration configuration, int recordLimit)
            throws IOException {
        return read(source, configuration, recordLimit, false);
    }

    @Override
    public ParseResult validate(
            FileDatasetParseSource source,
            FileDatasetParsingConfiguration configuration,
            int previewLimit
    ) throws IOException {
        return read(source, configuration, previewLimit, true);
    }

    /** Verifies declared Parquet uncompressed data before the parser begins scanning records. */
    static void requireUncompressedSizeWithin(Path path, long limit) {
        try {
            InputFile inputFile = new LocalInputFile(path);
            ParquetMetadata metadata = ParquetFileReader.readFooter(inputFile, ParquetMetadataConverter.NO_FILTER);
            long total = 0;
            for (BlockMetaData block : metadata.getBlocks()) {
                for (var column : block.getColumns()) {
                    total = Math.addExact(total, column.getTotalUncompressedSize());
                    if (total > limit) {
                        throw new FileDatasetParsingException("GeoParquet 解压后数据超过解析大小上限");
                    }
                }
            }
        } catch (FileDatasetParsingException exception) {
            throw exception;
        } catch (IOException | ArithmeticException exception) {
            throw new FileDatasetParsingException("无法读取 GeoParquet Footer", exception);
        }
    }

    private ParseResult read(
            FileDatasetParseSource source,
            FileDatasetParsingConfiguration configuration,
            int recordLimit,
            boolean validateAll
    ) throws IOException {
        if (!(configuration instanceof FileDatasetParsingConfiguration.GeoParquet)) {
            throw new FileDatasetParsingException("GeoParquet 解析参数无效");
        }
        if (recordLimit < 1) {
            throw new IllegalArgumentException("抽样记录数必须大于零");
        }

        Path path = FileDatasetParseSource.requireLocalFile(source);
        try {
            InputFile inputFile = new LocalInputFile(path);
            ParquetMetadata parquetMetadata = ParquetFileReader.readFooter(
                    inputFile, ParquetMetadataConverter.NO_FILTER
            );
            MessageType schema = parquetMetadata.getFileMetaData().getSchema();
            Metadata metadata = metadata(parquetMetadata, schema);
            Set<String> previewExcludedColumns = new LinkedHashSet<>(metadata.coveringColumns());
            previewExcludedColumns.add(metadata.geometryColumn());
            ParseResult parquet = parquetParser.parse(
                    source, new FileDatasetParsingConfiguration.Parquet(), recordLimit, previewExcludedColumns
            );
            GeometryScan scan = scan(inputFile, schema, metadata.geometryIndex(), validateAll ? Long.MAX_VALUE : recordLimit);
            if (validateAll && scan.rowCount() != parquet.rowCount()) {
                throw invalid("GeoParquet 记录数与 Footer 不一致");
            }
            GeometryKind kind = geometryKind(metadata.declaredGeometryTypes(), scan.geometryKinds());
            List<Field> fields = logicalFields(parquet.fields(), metadata, kind);
            List<Map<String, Object>> rows = previewRows(parquet.rows(), fields);
            Map<String, Object> sourceMetadata = new LinkedHashMap<>();
            sourceMetadata.put("geoParquet", true);
            sourceMetadata.put("geoParquetVersion", metadata.version());
            sourceMetadata.put("geometryField", metadata.geometryColumn());
            sourceMetadata.put("geometryKind", kind.name());
            sourceMetadata.put("crsAuthority", "EPSG");
            sourceMetadata.put("crsCode", metadata.epsgCode());
            sourceMetadata.put("coordinateDimension", CoordinateDimension.XY.name());
            sourceMetadata.put("geoParquetCoveringColumnHidden", !metadata.coveringColumns().isEmpty());
            return new ParseResult(
                    fields, rows, parquet.truncated(), true, sourceMetadata, parquet.rowCount()
            );
        } catch (FileDatasetParsingException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new FileDatasetParsingException("GeoParquet 文件内容无效", exception);
        } catch (RuntimeException exception) {
            throw new FileDatasetParsingException("无法读取 GeoParquet 文件", exception);
        }
    }

    private static List<Field> logicalFields(List<Field> source, Metadata metadata, GeometryKind kind) {
        List<Field> fields = new ArrayList<>();
        for (Field field : source) {
            if (metadata.coveringColumns().contains(field.name())) {
                continue;
            }
            PlatformTypeDefinition type = field.name().equals(metadata.geometryColumn())
                    ? PlatformTypeDefinition.geometry(new GeometryTypeDefinition(
                            kind, CrsReference.epsg(metadata.epsgCode()), CoordinateDimension.XY
                    ))
                    : field.type();
            fields.add(new Field(field.name(), fields.size(), type, field.nullable()));
        }
        return fields;
    }

    private static List<Map<String, Object>> previewRows(List<Map<String, Object>> source, List<Field> fields) {
        Set<String> names = new LinkedHashSet<>();
        for (Field field : fields) {
            if (field.type().type() != cn.superhuang.data.scalpel.contract.type.PlatformDataType.GEOMETRY) {
                names.add(field.name());
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>(source.size());
        for (Map<String, Object> row : source) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (String name : names) {
                values.put(name, row.get(name));
            }
            rows.add(values);
        }
        return rows;
    }

    private Metadata metadata(ParquetMetadata footer, MessageType schema) {
        String raw = footer.getFileMetaData().getKeyValueMetaData().get("geo");
        if (raw == null || raw.isBlank()) {
            throw invalid("缺少必需的 geo Footer 元数据");
        }
        Object decoded;
        try {
            decoded = objectMapper.readValue(raw, Object.class);
        } catch (RuntimeException exception) {
            throw invalid("geo Footer 元数据不是有效 JSON");
        }
        Map<String, Object> geo = object(decoded, "geo Footer 元数据必须是对象");
        String version = text(geo.get("version"), "geo.version 必须是字符串");
        if (!SUPPORTED_VERSIONS.contains(version)) {
            throw unsupported("只支持 GeoParquet 1.0.0 或 1.1.0，实际为 " + version);
        }
        String geometryColumn = text(geo.get("primary_column"), "geo.primary_column 必须是字符串");
        Map<String, Object> columns = object(geo.get("columns"), "geo.columns 必须是对象");
        if (columns.size() != 1 || !columns.containsKey(geometryColumn)) {
            throw unsupported("首版只支持一个 Geometry 列，且必须等于 geo.primary_column");
        }
        Map<String, Object> geometry = object(columns.get(geometryColumn), "Geometry 元数据必须是对象");
        if (!"WKB".equals(text(geometry.get("encoding"), "Geometry encoding 必须是字符串"))) {
            throw unsupported("首版只支持 WKB Geometry encoding");
        }
        List<String> declaredTypes = geometryTypes(geometry.get("geometry_types"));
        int geometryIndex = fieldIndex(schema, geometryColumn);
        Type geometryType = schema.getType(geometryIndex);
        if (!geometryType.isPrimitive()
                || geometryType.asPrimitiveType().getPrimitiveTypeName() != PrimitiveType.PrimitiveTypeName.BINARY
                || geometryType.getLogicalTypeAnnotation() != null
                || geometryType.getRepetition() == Type.Repetition.REPEATED) {
            throw invalid("Geometry 列必须是根级 REQUIRED 或 OPTIONAL BYTE_ARRAY");
        }
        int epsgCode = epsgCode(geometry);
        Object edges = geometry.get("edges");
        if (edges != null && !"planar".equals(edges)) {
            if ("spherical".equals(edges)) {
                throw unsupported("首版不支持 spherical edges");
            }
            throw invalid("Geometry edges 必须为 planar 或 spherical");
        }
        if (geometry.containsKey("epoch") || geometry.containsKey("coordinate_epoch")) {
            throw unsupported("首版不支持带 coordinate epoch 的动态 CRS");
        }
        return new Metadata(
                version, geometryColumn, geometryIndex, epsgCode, declaredTypes,
                coveringColumns(geometry.get("covering"), schema, geometryType)
        );
    }

    private static Set<String> coveringColumns(Object rawCovering, MessageType schema, Type geometryType) {
        if (rawCovering == null) {
            return Set.of();
        }
        Map<String, Object> covering = object(rawCovering, "Geometry covering 必须是对象");
        Object rawBbox = covering.get("bbox");
        if (rawBbox == null) {
            return Set.of();
        }
        Map<String, Object> bbox = object(rawBbox, "Geometry covering.bbox 必须是对象");
        if (!bbox.keySet().equals(new LinkedHashSet<>(BBOX_MEMBERS))) {
            throw invalid("Geometry covering.bbox 必须且只能包含 xmin/ymin/xmax/ymax");
        }
        String root = null;
        for (String member : BBOX_MEMBERS) {
            Object value = bbox.get(member);
            if (!(value instanceof List<?> path) || path.size() != 2
                    || !(path.get(0) instanceof String candidate) || !(path.get(1) instanceof String leaf)
                    || !member.equals(leaf)) {
                throw invalid("Geometry covering.bbox 路径无效");
            }
            if (root == null) {
                root = candidate;
            } else if (!root.equals(candidate)) {
                throw invalid("Geometry covering.bbox 必须引用同一个根字段");
            }
        }
        if (root == null || !schema.containsField(root)) {
            throw invalid("Geometry covering.bbox 引用的字段不存在");
        }
        Type type = schema.getType(root);
        if (type.isPrimitive() || type.getRepetition() != geometryType.getRepetition()) {
            throw invalid("Geometry covering.bbox 字段结构无效");
        }
        GroupType group = type.asGroupType();
        if (group.getFieldCount() != BBOX_MEMBERS.size()) {
            throw invalid("Geometry covering.bbox 字段必须包含四个 XY 成员");
        }
        PrimitiveType.PrimitiveTypeName coordinateType = null;
        for (int index = 0; index < BBOX_MEMBERS.size(); index++) {
            Type member = group.getType(index);
            if (!member.isPrimitive() || !BBOX_MEMBERS.get(index).equals(member.getName())) {
                throw invalid("Geometry covering.bbox 成员无效");
            }
            PrimitiveType.PrimitiveTypeName primitive = member.asPrimitiveType().getPrimitiveTypeName();
            if (primitive != PrimitiveType.PrimitiveTypeName.FLOAT
                    && primitive != PrimitiveType.PrimitiveTypeName.DOUBLE) {
                throw invalid("Geometry covering.bbox 坐标必须是 FLOAT 或 DOUBLE");
            }
            if (coordinateType == null) {
                coordinateType = primitive;
            } else if (coordinateType != primitive) {
                throw invalid("Geometry covering.bbox 坐标类型必须一致");
            }
        }
        return Set.of(root);
    }

    private static int epsgCode(Map<String, Object> geometry) {
        if (!geometry.containsKey("crs")) {
            return 4326;
        }
        Object rawCrs = geometry.get("crs");
        if (rawCrs == null) {
            throw unsupported("Geometry CRS 未定义，无法导入");
        }
        Map<String, Object> crs = object(rawCrs, "Geometry CRS 必须是 PROJJSON 对象");
        if (containsDynamicCrsState(crs)) {
            throw unsupported("首版不支持动态 CRS 或 coordinate epoch");
        }
        Map<String, Object> id = object(crs.get("id"), "Geometry CRS 必须包含根级 id");
        String authority = text(id.get("authority"), "Geometry CRS id.authority 必须是字符串");
        Object code = id.get("code");
        if ("OGC".equalsIgnoreCase(authority) && "CRS84".equals(String.valueOf(code))) {
            return 4326;
        }
        if (!"EPSG".equalsIgnoreCase(authority)) {
            throw unsupported("首版只支持 EPSG CRS 或 OGC:CRS84");
        }
        return positiveInteger(code, "Geometry CRS id.code 必须是正整数");
    }

    private static boolean containsDynamicCrsState(Object value) {
        if (value instanceof Map<?, ?> object) {
            for (Map.Entry<?, ?> entry : object.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object nested = entry.getValue();
                if ("epoch".equalsIgnoreCase(key) || "coordinate_epoch".equalsIgnoreCase(key)) {
                    return true;
                }
                if ("type".equalsIgnoreCase(key) && nested instanceof String type
                        && type.toLowerCase(java.util.Locale.ROOT).contains("dynamic")) {
                    return true;
                }
                if (containsDynamicCrsState(nested)) {
                    return true;
                }
            }
        } else if (value instanceof List<?> values) {
            for (Object nested : values) {
                if (containsDynamicCrsState(nested)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<String> geometryTypes(Object value) {
        if (!(value instanceof List<?> values)) {
            throw invalid("Geometry geometry_types 必须是数组");
        }
        LinkedHashSet<String> types = new LinkedHashSet<>();
        for (Object item : values) {
            String type = text(item, "Geometry geometry_types 元素必须是字符串");
            if (type.endsWith(" Z")) {
                throw unsupported("首版只支持二维 XY Geometry");
            }
            if (!geometryKind(type).isPresent()) {
                throw invalid("Geometry geometry_types 包含不支持的类型：" + type);
            }
            if (!types.add(type)) {
                throw invalid("Geometry geometry_types 不允许重复");
            }
        }
        return List.copyOf(types);
    }

    private static GeometryScan scan(InputFile inputFile, MessageType schema, int geometryIndex, long limit)
            throws IOException {
        Set<GeometryKind> kinds = new LinkedHashSet<>();
        long rowCount = 0;
        try (ParquetReader<Group> reader = new GroupReaderBuilder(inputFile).build()) {
            Group row;
            while ((row = reader.read()) != null) {
                rowCount++;
                if (rowCount > limit) {
                    break;
                }
                int values = row.getFieldRepetitionCount(geometryIndex);
                if (values == 0) {
                    continue;
                }
                if (values != 1) {
                    throw invalid("第 " + rowCount + " 条记录 Geometry 值数量无效");
                }
                Binary binary = row.getBinary(geometryIndex, 0);
                GeometryKind kind = WkbValidator.validate(binary.getBytes(), rowCount, schema.getType(geometryIndex).getName());
                kinds.add(kind);
            }
        }
        return new GeometryScan(rowCount > limit ? limit : rowCount, kinds);
    }

    private static GeometryKind geometryKind(List<String> declared, Set<GeometryKind> observed) {
        if (!declared.isEmpty()) {
            Set<GeometryKind> expected = new LinkedHashSet<>();
            for (String value : declared) {
                expected.add(geometryKind(value).orElseThrow());
            }
            if (!expected.containsAll(observed)) {
                throw invalid("Geometry geometry_types 与实际 WKB 类型不一致");
            }
            return expected.size() == 1 ? expected.iterator().next() : GeometryKind.GEOMETRY;
        }
        return observed.size() == 1 ? observed.iterator().next() : GeometryKind.GEOMETRY;
    }

    private static java.util.Optional<GeometryKind> geometryKind(String value) {
        return switch (value) {
            case "Point" -> java.util.Optional.of(GeometryKind.POINT);
            case "LineString" -> java.util.Optional.of(GeometryKind.LINESTRING);
            case "Polygon" -> java.util.Optional.of(GeometryKind.POLYGON);
            case "MultiPoint" -> java.util.Optional.of(GeometryKind.MULTIPOINT);
            case "MultiLineString" -> java.util.Optional.of(GeometryKind.MULTILINESTRING);
            case "MultiPolygon" -> java.util.Optional.of(GeometryKind.MULTIPOLYGON);
            case "GeometryCollection" -> java.util.Optional.of(GeometryKind.GEOMETRYCOLLECTION);
            default -> java.util.Optional.empty();
        };
    }

    private static int fieldIndex(MessageType schema, String name) {
        for (int index = 0; index < schema.getFieldCount(); index++) {
            if (schema.getType(index).getName().equals(name)) {
                return index;
            }
        }
        throw invalid("Geometry 列不存在：" + name);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String message) {
        if (!(value instanceof Map<?, ?> values)) {
            throw invalid(message);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        values.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private static String text(Object value, String message) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw invalid(message);
        }
        return text;
    }

    private static int positiveInteger(Object value, String message) {
        try {
            long number;
            if (value instanceof Number numberValue) {
                number = numberValue.longValue();
                if (numberValue.doubleValue() != number) throw new NumberFormatException();
            } else if (value instanceof String text && text.matches("[1-9][0-9]*")) {
                number = Long.parseLong(text);
            } else {
                throw new NumberFormatException();
            }
            if (number < 1 || number > Integer.MAX_VALUE) throw new NumberFormatException();
            return (int) number;
        } catch (NumberFormatException exception) {
            throw invalid(message);
        }
    }

    private static FileDatasetParsingException invalid(String message) {
        return new FileDatasetParsingException("GeoParquet 文件无效：" + message);
    }

    private static FileDatasetParsingException unsupported(String message) {
        return new FileDatasetParsingException("GeoParquet 当前不支持：" + message);
    }

    private record Metadata(
            String version,
            String geometryColumn,
            int geometryIndex,
            int epsgCode,
            List<String> declaredGeometryTypes,
            Set<String> coveringColumns
    ) {
        private Metadata {
            Objects.requireNonNull(version);
            Objects.requireNonNull(geometryColumn);
            declaredGeometryTypes = List.copyOf(declaredGeometryTypes);
            coveringColumns = Set.copyOf(coveringColumns);
        }
    }

    private record GeometryScan(long rowCount, Set<GeometryKind> geometryKinds) {
        private GeometryScan {
            geometryKinds = Set.copyOf(geometryKinds);
        }
    }

    private static final class GroupReaderBuilder extends ParquetReader.Builder<Group> {
        private GroupReaderBuilder(InputFile inputFile) {
            super(inputFile);
        }

        @Override
        protected ReadSupport<Group> getReadSupport() {
            return new GroupReadSupport();
        }
    }

    /** Parses only the standard XY WKB structure; it never uses JTS repair behaviour. */
    private static final class WkbValidator {
        private final ByteBuffer input;
        private final long rowNumber;
        private final String fieldName;

        private WkbValidator(byte[] bytes, long rowNumber, String fieldName) {
            this.input = ByteBuffer.wrap(bytes);
            this.rowNumber = rowNumber;
            this.fieldName = fieldName;
        }

        static GeometryKind validate(byte[] bytes, long rowNumber, String fieldName) {
            try {
                WkbValidator validator = new WkbValidator(bytes, rowNumber, fieldName);
                GeometryKind kind = validator.geometry(null);
                if (validator.input.hasRemaining()) {
                    throw validator.failure("WKB 包含尾随字节");
                }
                return kind;
            } catch (FileDatasetParsingException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw new FileDatasetParsingException(
                        "GeoParquet 第 " + rowNumber + " 条记录 Geometry 字段 " + fieldName + " WKB 结构无效", exception
                );
            }
        }

        private GeometryKind geometry(Integer expectedType) {
            int order = unsignedByte("缺少 WKB 字节序");
            if (order == 0) {
                input.order(ByteOrder.BIG_ENDIAN);
            } else if (order == 1) {
                input.order(ByteOrder.LITTLE_ENDIAN);
            } else {
                throw failure("WKB 字节序无效");
            }
            int rawType = integer("缺少 WKB Geometry 类型");
            if ((rawType & 0xE0000000) != 0) {
                throw unsupportedWkb("不支持 EWKB 标志位");
            }
            if (rawType >= 1000) {
                throw unsupportedWkb("只支持二维 XY WKB");
            }
            if (rawType < 1 || rawType > 7) {
                throw failure("WKB Geometry 类型无效");
            }
            if (expectedType != null && rawType != expectedType) {
                throw failure("WKB Multi Geometry 成员类型不一致");
            }
            GeometryKind kind = switch (rawType) {
                case 1 -> GeometryKind.POINT;
                case 2 -> GeometryKind.LINESTRING;
                case 3 -> GeometryKind.POLYGON;
                case 4 -> GeometryKind.MULTIPOINT;
                case 5 -> GeometryKind.MULTILINESTRING;
                case 6 -> GeometryKind.MULTIPOLYGON;
                case 7 -> GeometryKind.GEOMETRYCOLLECTION;
                default -> throw failure("WKB Geometry 类型无效");
            };
            switch (rawType) {
                case 1 -> point();
                case 2 -> line();
                case 3 -> polygon();
                case 4 -> multiple(1);
                case 5 -> multiple(2);
                case 6 -> multiple(3);
                case 7 -> collection();
                default -> throw failure("WKB Geometry 类型无效");
            }
            return kind;
        }

        private void point() {
            double x = coordinate();
            double y = coordinate();
            if (Double.isNaN(x) && Double.isNaN(y)) {
                return;
            }
            requireFinite(x);
            requireFinite(y);
        }

        private void line() {
            int count = count("LineString 坐标数量无效");
            if (count == 0) return;
            if (count < 2) throw failure("LineString 至少需要两个坐标");
            for (int index = 0; index < count; index++) pointCoordinate();
        }

        private void polygon() {
            int ringCount = count("Polygon 环数量无效");
            for (int ring = 0; ring < ringCount; ring++) {
                int count = count("Polygon 环坐标数量无效");
                if (count < 4) throw failure("Polygon 环至少需要四个坐标");
                double firstX = coordinate();
                double firstY = coordinate();
                requireFinite(firstX);
                requireFinite(firstY);
                double lastX = firstX;
                double lastY = firstY;
                for (int index = 1; index < count; index++) {
                    lastX = coordinate();
                    lastY = coordinate();
                    requireFinite(lastX);
                    requireFinite(lastY);
                }
                if (firstX != lastX || firstY != lastY) {
                    throw failure("Polygon 环必须闭合");
                }
            }
        }

        private void multiple(int expectedType) {
            int count = count("Multi Geometry 成员数量无效");
            for (int index = 0; index < count; index++) geometry(expectedType);
        }

        private void collection() {
            int count = count("GeometryCollection 成员数量无效");
            for (int index = 0; index < count; index++) geometry(null);
        }

        private void pointCoordinate() {
            double x = coordinate();
            double y = coordinate();
            requireFinite(x);
            requireFinite(y);
        }

        private double coordinate() {
            requireRemaining(Double.BYTES, "WKB 坐标不完整");
            return input.getDouble();
        }

        private int count(String message) {
            int value = integer(message);
            if (value < 0) throw failure(message);
            return value;
        }

        private int integer(String message) {
            requireRemaining(Integer.BYTES, message);
            return input.getInt();
        }

        private int unsignedByte(String message) {
            requireRemaining(1, message);
            return Byte.toUnsignedInt(input.get());
        }

        private void requireFinite(double value) {
            if (!Double.isFinite(value)) throw failure("WKB 包含非有限 XY 坐标");
        }

        private void requireRemaining(int size, String message) {
            if (input.remaining() < size) throw failure(message);
        }

        private FileDatasetParsingException failure(String message) {
            return new FileDatasetParsingException(
                    "GeoParquet 第 " + rowNumber + " 条记录 Geometry 字段 " + fieldName + " " + message
            );
        }

        private FileDatasetParsingException unsupportedWkb(String message) {
            return new FileDatasetParsingException(
                    "GeoParquet 当前不支持：第 " + rowNumber + " 条记录 Geometry 字段 " + fieldName + " " + message
            );
        }
    }
}
