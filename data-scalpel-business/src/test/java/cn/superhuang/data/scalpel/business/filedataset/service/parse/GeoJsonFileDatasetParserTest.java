package cn.superhuang.data.scalpel.business.filedataset.service.parse;

import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoJsonFileDatasetParserTest {

    private final GeoJsonFileDatasetParser parser = new GeoJsonFileDatasetParser(new ObjectMapper());

    @Test
    void fullyScansFeatureCollectionAndKeepsPropertiesAndTopLevelIdSeparate() throws Exception {
        FileDatasetParser.ParseResult result = validate("""
                {
                  "type":"FeatureCollection",
                  "features":[
                    {
                      "type":"Feature",
                      "id":"feature-001",
                      "properties":{"name":"海淀","details":{"level":2},"id":"property-id"},
                      "geometry":{"type":"Point","coordinates":[116.3,39.9]}
                    },
                    {
                      "type":"Feature",
                      "properties":{"name":"朝阳"},
                      "geometry":null
                    }
                  ]
                }
                """, 1000);

        assertEquals(2, result.rowCount());
        assertFalse(result.truncated());
        assertEquals(List.of("name", "details", "id", "_feature_id", "geometry"),
                result.fields().stream().map(FileDatasetParser.Field::name).toList());
        assertEquals(PlatformDataType.STRING, result.fields().get(1).type().type());
        assertTrue(result.fields().get(1).nullable());
        assertEquals("{\"level\":2}", result.rows().getFirst().get("details"));
        assertEquals("property-id", result.rows().getFirst().get("id"));
        assertEquals("feature-001", result.rows().getFirst().get("_feature_id"));
        assertEquals(GeometryKind.POINT, result.fields().getLast().type().geometry().kind());
        assertEquals(4490, result.fields().getLast().type().geometry().crs().code());
        assertEquals(CoordinateDimension.XY, result.fields().getLast().type().geometry().dimension());
        assertEquals("geometry", result.sourceMetadata().get("geometryField"));
    }

    @Test
    void preservesLargeNumericFeatureIdsWithoutLosingPrecision() throws Exception {
        FileDatasetParser.ParseResult result = validate("""
                {"type":"FeatureCollection","features":[
                  {"type":"Feature","id":123456789012345678901,"properties":{"name":"A"},
                   "geometry":{"type":"Point","coordinates":[0,0]}}
                ]}
                """, 10);

        assertEquals("123456789012345678901", result.rows().getFirst().get("_feature_id"));
    }

    @Test
    void marksPreviewAsTruncatedWhenRootTypeFollowsTheFeaturesArray() throws Exception {
        FileDatasetParser.ParseResult result = parser.parse(
                new FileDatasetParseSource.Stream(new ByteArrayInputStream(("""
                        {"features":[
                        """ + feature("[0,0]") + "," + feature("[1,1]") + """
                        ],"type":"FeatureCollection"}
                        """).getBytes(StandardCharsets.UTF_8))),
                new FileDatasetParsingConfiguration.GeoJson(4326),
                1
        );

        assertTrue(result.truncated());
        assertEquals(1, result.rowCount());
        assertEquals(1, result.rows().size());
    }

    @Test
    void validatesFeaturesAfterThePreviewWindow() {
        StringBuilder features = new StringBuilder();
        for (int index = 1; index <= 1001; index++) {
            if (!features.isEmpty()) {
                features.append(',');
            }
            features.append(feature("[116.3,39.9]"));
        }
        features.append(',').append(feature("[116.3,39.9,9]"));

        FileDatasetParsingException exception = assertThrows(
                FileDatasetParsingException.class,
                () -> validate("{\"type\":\"FeatureCollection\",\"features\":[" + features + "]}", 1000)
        );

        assertTrue(exception.getMessage().contains("features[1002]"));
        assertTrue(exception.getMessage().contains("恰好包含 X 和 Y 两个值"));
    }

    @Test
    void acceptsNullPropertiesAndGeometryButRejectsInvalidCollectionShapes() throws Exception {
        FileDatasetParser.ParseResult nullable = validate("""
                {"type":"FeatureCollection","features":[
                  {"type":"Feature","properties":null,"geometry":null}
                ]}
                """, 10);
        assertEquals(List.of("_feature_id", "geometry"),
                nullable.fields().stream().map(FileDatasetParser.Field::name).toList());
        assertEquals(GeometryKind.GEOMETRY, nullable.fields().getLast().type().geometry().kind());

        assertInvalid("{\"type\":\"Feature\",\"features\":[]}", "FeatureCollection");
        assertInvalid("{\"type\":\"FeatureCollection\",\"features\":[]}", "不包含任何 Feature");
        assertInvalid("""
                {"type":"FeatureCollection","features":[
                  {"type":"Feature","properties":{"geometry":"reserved"},
                   "geometry":{"type":"Point","coordinates":[0,0]}}
                ]}
                """, "保留字段");
    }

    private FileDatasetParser.ParseResult validate(String document, int previewLimit) throws Exception {
        return parser.validate(
                new FileDatasetParseSource.Stream(new ByteArrayInputStream(document.getBytes(StandardCharsets.UTF_8))),
                new FileDatasetParsingConfiguration.GeoJson(4490),
                previewLimit
        );
    }

    private void assertInvalid(String document, String message) {
        FileDatasetParsingException exception = assertThrows(
                FileDatasetParsingException.class,
                () -> validate(document, 10)
        );
        assertTrue(exception.getMessage().contains(message));
    }

    private static String feature(String coordinates) {
        return "{\"type\":\"Feature\",\"properties\":{\"name\":\"A\"},"
                + "\"geometry\":{\"type\":\"Point\",\"coordinates\":" + coordinates + "}}";
    }
}
