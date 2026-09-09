package cn.superhuang.data.scalpel.business.filedataset.service.parse;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFormat;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileRecordDelimiter;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import org.springframework.stereotype.Component;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Streams one complete RFC 7946 Feature from each nonblank physical line. */
@Component
public class GeoJsonLinesFileDatasetParser implements FileDatasetParser {

    private final ObjectMapper objectMapper;

    public GeoJsonLinesFileDatasetParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(FileDatasetFormat format) {
        return format == FileDatasetFormat.GEOJSONL;
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
        if (!(configuration instanceof FileDatasetParsingConfiguration.GeoJsonLines geoJsonLines)) {
            throw new FileDatasetParsingException("GEOJSONL 解析参数无效");
        }
        InputStream input = FileDatasetParseSource.requireStream(source);
        FieldCollector collector = new FieldCollector(recordLimit);
        List<String> previewFeatureIds = new ArrayList<>();
        Set<GeometryKind> geometryKinds = new LinkedHashSet<>();
        long[] physicalLine = {0};
        boolean[] truncated = {false};

        try {
            RecordReader.forEachLine(
                    new InputStreamReader(input, StandardCharsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)),
                    FileRecordDelimiter.AUTO,
                    line -> {
                        physicalLine[0]++;
                        if (line.isBlank()) return;
                        if (!validateAll && collector.rowCount() >= recordLimit) {
                            truncated[0] = true;
                            return;
                        }
                        GeoJsonFeatureSupport.Feature feature = readLine(
                                line, physicalLine[0], validateAll
                        );
                        JsonValueSupport.addValue(collector, feature.properties(), objectMapper);
                        if (previewFeatureIds.size() < recordLimit) previewFeatureIds.add(feature.id());
                        if (feature.geometryKind() != null) geometryKinds.add(feature.geometryKind());
                    },
                    () -> truncated[0]
            );
        } catch (CharacterCodingException exception) {
            throw new FileDatasetParsingException("GEOJSONL 文件必须使用 UTF-8 编码", exception);
        } catch (FileDatasetParsingException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new FileDatasetParsingException("GEOJSONL 内容读取失败", exception);
        }
        if (collector.rowCount() == 0) {
            throw new FileDatasetParsingException("GEOJSONL 文件不包含任何 Feature");
        }
        if (validateAll) truncated[0] = collector.rowCount() > recordLimit;
        return result(collector, previewFeatureIds, geometryKinds, truncated[0], geoJsonLines.epsgCode());
    }

    private GeoJsonFeatureSupport.Feature readLine(String line, long physicalLine, boolean validateGeometry) {
        String path = "GEOJSONL 第 " + physicalLine + " 行";
        if (line.stripLeading().startsWith("\u001E")) {
            throw new FileDatasetParsingException(path + " 不支持 RS 分隔的 GeoJSON Text Sequence");
        }
        try (JsonParser parser = objectMapper.createParser(line)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new FileDatasetParsingException(path + " 必须是一个完整 GeoJSON Feature 对象");
            }
            GeoJsonFeatureSupport.Feature feature = GeoJsonFeatureSupport.readFeature(parser, path, validateGeometry);
            if (parser.nextToken() != null) {
                throw new FileDatasetParsingException(path + " 只能包含一个完整 GeoJSON Feature");
            }
            return feature;
        } catch (FileDatasetParsingException exception) {
            throw exception;
        } catch (RuntimeException | IOException exception) {
            throw new FileDatasetParsingException(path + " JSON 格式无效", exception);
        }
    }

    private static ParseResult result(
            FieldCollector collector,
            List<String> featureIds,
            Set<GeometryKind> geometryKinds,
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
                "geoJsonLines", true,
                "geometryField", GeoJsonFeatureSupport.GEOMETRY_FIELD,
                "geometryKind", geometryKind.name(),
                "crsAuthority", "EPSG",
                "crsCode", epsgCode,
                "coordinateDimension", CoordinateDimension.XY.name()
        );
        return new ParseResult(fields, rows, truncated, true, metadata, collector.rowCount());
    }
}
