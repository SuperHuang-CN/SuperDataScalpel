package cn.superhuang.data.scalpel.business.filedataset.service.parse;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFormat;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Streams an RFC 7946 FeatureCollection without retaining the complete source document. */
@org.springframework.stereotype.Component
public class GeoJsonFileDatasetParser implements FileDatasetParser {

    private final ObjectMapper objectMapper;

    public GeoJsonFileDatasetParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(FileDatasetFormat format) {
        return format == FileDatasetFormat.GEOJSON;
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

    private ParseResult read(
            FileDatasetParseSource source,
            FileDatasetParsingConfiguration configuration,
            int recordLimit,
            boolean validateAll
    ) throws IOException {
        if (!(configuration instanceof FileDatasetParsingConfiguration.GeoJson geoJson)) {
            throw new FileDatasetParsingException("GeoJSON 解析参数无效");
        }
        InputStream input = FileDatasetParseSource.requireStream(source);
        FieldCollector collector = new FieldCollector(recordLimit);
        List<String> previewFeatureIds = new ArrayList<>();
        Set<GeometryKind> geometryKinds = new LinkedHashSet<>();
        long featureCount = 0;
        boolean featuresSeen = false;
        boolean truncated = false;
        String rootType = null;

        try (JsonParser parser = objectMapper.createParser(input)) {
            require(parser.nextToken(), JsonToken.START_OBJECT, "根节点必须是 GeoJSON FeatureCollection 对象");
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                if (parser.currentToken() != JsonToken.PROPERTY_NAME) {
                    throw invalid("根节点结构无效");
                }
                String name = parser.currentName();
                JsonToken valueToken = parser.nextToken();
                if ("type".equals(name)) {
                    if (rootType != null) throw invalid("根节点 type 重复");
                    require(valueToken, JsonToken.VALUE_STRING, "根节点 type 必须是字符串");
                    rootType = parser.getText();
                    continue;
                }
                if (!"features".equals(name)) {
                    parser.skipChildren();
                    continue;
                }
                if (featuresSeen) throw invalid("根节点 features 重复");
                featuresSeen = true;
                require(valueToken, JsonToken.START_ARRAY, "features 必须是数组");
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    if (!validateAll && featureCount >= recordLimit) {
                        parser.skipChildren();
                        truncated = true;
                        continue;
                    }
                    long featureNumber = featureCount + 1;
                    GeoJsonFeatureSupport.Feature feature = GeoJsonFeatureSupport.readFeature(
                            parser, "GeoJSON features[" + featureNumber + "]", validateAll
                    );
                    JsonValueSupport.addValue(collector, feature.properties(), objectMapper);
                    if (previewFeatureIds.size() < recordLimit) previewFeatureIds.add(feature.id());
                    if (feature.geometryKind() != null) geometryKinds.add(feature.geometryKind());
                    featureCount++;
                }
            }
            if (parser.nextToken() != null) throw invalid("根节点之后不允许存在额外 JSON 内容");
        } catch (FileDatasetParsingException exception) {
            throw exception;
        } catch (RuntimeException | IOException exception) {
            throw new FileDatasetParsingException("GeoJSON 内容无效", exception);
        }
        if (!"FeatureCollection".equals(rootType)) throw invalid("根节点 type 必须为 FeatureCollection");
        if (!featuresSeen) throw invalid("根节点缺少 features 数组");
        if (featureCount == 0) throw invalid("FeatureCollection 不包含任何 Feature");
        if (validateAll) truncated = featureCount > recordLimit;
        return result(collector, previewFeatureIds, geometryKinds, featureCount, truncated, geoJson.epsgCode());
    }

    private static ParseResult result(
            FieldCollector collector,
            List<String> featureIds,
            Set<GeometryKind> geometryKinds,
            long featureCount,
            boolean truncated,
            int epsgCode
    ) {
        List<Field> fields = new ArrayList<>(collector.fields());
        fields.add(new Field(
                GeoJsonFeatureSupport.FEATURE_ID_FIELD, fields.size(), PlatformTypeDefinition.string(null), true
        ));
        GeometryKind geometryKind = geometryKinds.size() == 1 ? geometryKinds.iterator().next() : GeometryKind.GEOMETRY;
        fields.add(new Field(
                GeoJsonFeatureSupport.GEOMETRY_FIELD,
                fields.size(),
                PlatformTypeDefinition.geometry(new GeometryTypeDefinition(
                        geometryKind, CrsReference.epsg(epsgCode), CoordinateDimension.XY
                )),
                true
        ));
        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, Object>> propertyRows = collector.rows();
        for (int index = 0; index < propertyRows.size(); index++) {
            LinkedHashMap<String, Object> row = new LinkedHashMap<>(propertyRows.get(index));
            row.put(GeoJsonFeatureSupport.FEATURE_ID_FIELD, featureIds.get(index));
            rows.add(row);
        }
        Map<String, Object> metadata = Map.of(
                "geoJsonFeatureCollection", true,
                "geometryField", GeoJsonFeatureSupport.GEOMETRY_FIELD,
                "geometryKind", geometryKind.name(),
                "crsAuthority", "EPSG",
                "crsCode", epsgCode,
                "coordinateDimension", CoordinateDimension.XY.name()
        );
        return new ParseResult(fields, rows, truncated, true, metadata, featureCount);
    }

    private static void require(JsonToken actual, JsonToken expected, String message) {
        if (actual != expected) throw invalid(message);
    }

    private static FileDatasetParsingException invalid(String message) {
        return new FileDatasetParsingException("GeoJSON " + message);
    }
}
