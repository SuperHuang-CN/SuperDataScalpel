package cn.superhuang.data.scalpel.business.filedataset.service.parse;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetField;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileRecordDelimiter;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileDatasetSchemaValidatorTest {

    private final FileDatasetSchemaValidator validator = new FileDatasetSchemaValidator();

    @Test
    void comparesFieldCountNameOrderTypeAndNullableExactly() {
        UUID tableId = UUID.randomUUID();
        List<FileDatasetField> expected = List.of(
                FileDatasetField.create(
                        tableId, "id", 0, PlatformTypeDefinition.of(PlatformDataType.LONG), false
                ),
                FileDatasetField.create(
                        tableId, "name", 1, PlatformTypeDefinition.string(null), true
                )
        );
        FileDatasetParser.ParseResult compatible = result(
                field("id", 0, PlatformDataType.LONG, false),
                field("name", 1, PlatformDataType.STRING, true)
        );
        validator.requireCompatible(expected, compatible, Map.of());

        FileDatasetParsingException reordered = assertThrows(
                FileDatasetParsingException.class,
                () -> validator.requireCompatible(expected, result(
                        field("name", 0, PlatformDataType.STRING, true),
                        field("id", 1, PlatformDataType.LONG, false)
                ), Map.of())
        );
        assertTrue(reordered.getMessage().contains("第 1 个字段名称"));

        FileDatasetParsingException nullable = assertThrows(
                FileDatasetParsingException.class,
                () -> validator.requireCompatible(expected, result(
                        field("id", 0, PlatformDataType.LONG, true),
                        field("name", 1, PlatformDataType.STRING, true)
                ), Map.of())
        );
        assertTrue(nullable.getMessage().contains("nullable"));

        assertEquals(validator.fingerprint(compatible.fields()), validator.fingerprint(compatible.fields()));
        assertNotEquals(
                validator.fingerprint(compatible.fields()),
                validator.fingerprint(result(field("id", 0, PlatformDataType.STRING, false)).fields())
        );
    }

    @Test
    void csvValidationScansPastThePreviewWindowAndExposesLateTypeDrift() throws Exception {
        StringBuilder csv = new StringBuilder("id,amount\n");
        for (int row = 1; row <= 1001; row++) {
            csv.append(row).append(',').append(row).append('\n');
        }
        csv.append("1002,not-a-number\n");
        CsvFileDatasetParser parser = new CsvFileDatasetParser();
        FileDatasetParser.ParseResult result = parser.validate(
                new FileDatasetParseSource.Stream(new ByteArrayInputStream(
                        csv.toString().getBytes(StandardCharsets.UTF_8)
                )),
                new FileDatasetParsingConfiguration.Csv(
                        "UTF-8", ",", FileRecordDelimiter.AUTO, "\"", "\\", true
                ),
                1000
        );

        assertEquals(1002, result.rowCount());
        assertEquals(1000, result.rows().size());
        assertTrue(result.truncated());
        assertEquals(PlatformDataType.STRING, result.fields().get(1).type().type());

        List<FileDatasetField> authoritative = List.of(
                FileDatasetField.create(
                        UUID.randomUUID(), "id", 0, PlatformTypeDefinition.of(PlatformDataType.LONG), false
                ),
                FileDatasetField.create(
                        UUID.randomUUID(), "amount", 1, PlatformTypeDefinition.of(PlatformDataType.LONG), false
                )
        );
        FileDatasetParsingException mismatch = assertThrows(
                FileDatasetParsingException.class,
                () -> validator.requireCompatible(authoritative, result, Map.of())
        );
        assertTrue(mismatch.getMessage().contains("第 2 个字段平台类型"));
    }

    @Test
    void comparesShapefileShapeDimensionsGeometryFieldAndNormalizedWkt() {
        List<FileDatasetField> expected = List.of(FileDatasetField.create(
                UUID.randomUUID(), "_geometry", 0, PlatformTypeDefinition.string(null), false
        ));
        Map<String, Object> expectedMetadata = Map.of(
                "shapeType", "POINT_Z",
                "shapeHasZ", true,
                "shapeHasM", false,
                "geometryField", "_geometry",
                "spatialReference", Map.of("normalizedWkt", "GEOGCS[ \"CGCS2000\" ]")
        );
        FileDatasetParser.ParseResult compatible = new FileDatasetParser.ParseResult(
                List.of(field("_geometry", 0, PlatformDataType.STRING, false)),
                List.of(),
                false,
                true,
                Map.of(
                        "shapeType", "POINT_Z",
                        "shapeHasZ", true,
                        "shapeHasM", false,
                        "geometryField", "_geometry",
                        "spatialReference", Map.of("wkt", " GEOGCS[   \"CGCS2000\"   ] ")
                ),
                0
        );
        validator.requireCompatible(expected, compatible, expectedMetadata);

        FileDatasetParsingException mismatch = assertThrows(
                FileDatasetParsingException.class,
                () -> validator.requireCompatible(
                        expected,
                        new FileDatasetParser.ParseResult(
                                compatible.fields(), List.of(), false, true,
                                Map.of(
                                        "shapeType", "POINT_Z",
                                        "shapeHasZ", false,
                                        "shapeHasM", false,
                                        "geometryField", "_geometry",
                                        "spatialReference", Map.of("wkt", "GEOGCS[ \"CGCS2000\" ]")
                                ),
                                0
                        ),
                        expectedMetadata
                )
        );
        assertTrue(mismatch.getMessage().contains("Z 维度"));
    }

    @Test
    void includesTheCompleteGeometryDefinitionInCompatibilityAndFingerprinting() {
        FileDatasetParser.ParseResult epsg4326 = geometryResult(4326, CoordinateDimension.XY);
        FileDatasetParser.ParseResult epsg3857 = geometryResult(3857, CoordinateDimension.XY);
        FileDatasetParser.ParseResult xyz = geometryResult(4326, CoordinateDimension.XYZ);

        validator.requireCompatible(epsg4326, geometryResult(4326, CoordinateDimension.XY));
        assertThrows(FileDatasetParsingException.class, () -> validator.requireCompatible(epsg4326, epsg3857));
        assertNotEquals(validator.fingerprint(epsg4326.fields()), validator.fingerprint(epsg3857.fields()));
        assertNotEquals(validator.fingerprint(epsg4326.fields()), validator.fingerprint(xyz.fields()));
    }

    private static FileDatasetParser.ParseResult result(FileDatasetParser.Field... fields) {
        return new FileDatasetParser.ParseResult(List.of(fields), List.of(), false, true, Map.of(), 0);
    }

    private static FileDatasetParser.Field field(
            String name,
            int order,
            PlatformDataType type,
            boolean nullable
    ) {
        return new FileDatasetParser.Field(name, order, PlatformTypeDefinition.of(type), nullable);
    }

    private static FileDatasetParser.ParseResult geometryResult(
            int epsgCode,
            CoordinateDimension dimension
    ) {
        PlatformTypeDefinition type = PlatformTypeDefinition.geometry(new GeometryTypeDefinition(
                GeometryKind.MULTIPOLYGON, CrsReference.epsg(epsgCode), dimension
        ));
        return new FileDatasetParser.ParseResult(
                List.of(new FileDatasetParser.Field("shape", 0, type, true)),
                List.of(), false, true, Map.of(), 0
        );
    }
}
