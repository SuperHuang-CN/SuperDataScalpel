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

class GeoJsonLinesFileDatasetParserTest {

    private final GeoJsonLinesFileDatasetParser parser = new GeoJsonLinesFileDatasetParser(new ObjectMapper());

    @Test
    void fullyScansNonblankPhysicalLinesAndKeepsFeatureIdSeparate() throws Exception {
        FileDatasetParser.ParseResult result = validate("""
                {"type":"Feature","id":123456789012345678901,"properties":{"name":"海淀","details":{"level":2},"id":"property-id"},"geometry":{"type":"Point","coordinates":[116.3,39.9]}}

                {"type":"Feature","properties":null,"geometry":null}
                """.replace("\n", "\r\n"), 1000);

        assertEquals(2, result.rowCount());
        assertFalse(result.truncated());
        assertEquals(List.of("name", "details", "id", "_feature_id", "geometry"),
                result.fields().stream().map(FileDatasetParser.Field::name).toList());
        assertEquals(PlatformDataType.STRING, result.fields().get(1).type().type());
        assertEquals("{\"level\":2}", result.rows().getFirst().get("details"));
        assertEquals("property-id", result.rows().getFirst().get("id"));
        assertEquals("123456789012345678901", result.rows().getFirst().get("_feature_id"));
        assertEquals(GeometryKind.POINT, result.fields().getLast().type().geometry().kind());
        assertEquals(4490, result.fields().getLast().type().geometry().crs().code());
        assertEquals(CoordinateDimension.XY, result.fields().getLast().type().geometry().dimension());
        assertFalse(result.rows().getFirst().containsKey("geometry"));
        assertEquals(Boolean.TRUE, result.sourceMetadata().get("geoJsonLines"));
    }

    @Test
    void validatesRowsAfterThePreviewWindow() {
        StringBuilder lines = new StringBuilder();
        for (int index = 0; index < 1001; index++) {
            lines.append(feature("[116.3,39.9]")).append('\n');
        }
        lines.append(feature("[116.3,39.9,9]"));

        FileDatasetParsingException exception = assertThrows(
                FileDatasetParsingException.class,
                () -> validate(lines.toString(), 1000)
        );

        assertTrue(exception.getMessage().contains("第 1002 行"));
        assertTrue(exception.getMessage().contains("恰好包含 X 和 Y 两个值"));
    }

    @Test
    void rejectsNonFeatureSequenceAndLineFramingViolations() {
        assertInvalid("{\"type\":\"FeatureCollection\",\"features\":[]}", "必须为 Feature");
        assertInvalid("{\"type\":\"Feature\",\"properties\":{},\"geometry\":null}{\"type\":\"Feature\",\"properties\":{},\"geometry\":null}", "只能包含一个完整");
        assertInvalid("\u001E{\"type\":\"Feature\",\"properties\":{},\"geometry\":null}", "GeoJSON Text Sequence");
        assertInvalid("", "不包含任何 Feature");
    }

    private FileDatasetParser.ParseResult validate(String content, int previewLimit) throws Exception {
        return parser.validate(
                new FileDatasetParseSource.Stream(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))),
                new FileDatasetParsingConfiguration.GeoJsonLines(4490),
                previewLimit
        );
    }

    private void assertInvalid(String content, String message) {
        FileDatasetParsingException exception = assertThrows(
                FileDatasetParsingException.class,
                () -> validate(content, 10)
        );
        assertTrue(exception.getMessage().contains(message));
    }

    private static String feature(String coordinates) {
        return "{\"type\":\"Feature\",\"properties\":{\"name\":\"A\"},"
                + "\"geometry\":{\"type\":\"Point\",\"coordinates\":" + coordinates + "}}";
    }
}
