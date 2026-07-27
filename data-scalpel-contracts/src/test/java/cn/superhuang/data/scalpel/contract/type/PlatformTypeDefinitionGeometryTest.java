package cn.superhuang.data.scalpel.contract.type;

import cn.superhuang.data.scalpel.contract.service.SqlServiceParameterDefinition;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlatformTypeDefinitionGeometryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void constructsEveryGeometryKindAndNormalizesTheCrsAuthority() {
        for (GeometryKind kind : GeometryKind.values()) {
            PlatformTypeDefinition definition = PlatformTypeDefinition.geometry(
                    new GeometryTypeDefinition(
                            kind,
                            new CrsReference(" epsg ", 4326),
                            CoordinateDimension.XY
                    )
            );

            assertEquals(PlatformDataType.GEOMETRY, definition.type());
            assertEquals(kind, definition.geometry().kind());
            assertEquals("EPSG", definition.geometry().crs().authority());
            assertNull(definition.length());
            assertNull(definition.precision());
            assertNull(definition.scale());
        }
    }

    @Test
    void rejectsInvalidGeometryAndScalarParameterCombinations() {
        GeometryTypeDefinition geometry = new GeometryTypeDefinition(
                GeometryKind.POINT,
                CrsReference.epsg(4326),
                CoordinateDimension.XY
        );

        assertThrows(IllegalArgumentException.class, () ->
                new PlatformTypeDefinition(PlatformDataType.GEOMETRY, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () ->
                new PlatformTypeDefinition(PlatformDataType.GEOMETRY, 10, null, null, geometry));
        assertThrows(IllegalArgumentException.class, () ->
                new PlatformTypeDefinition(PlatformDataType.STRING, null, null, null, geometry));
        assertThrows(IllegalArgumentException.class, () -> new CrsReference("EPSG", 0));
    }

    @Test
    void roundTripsGeometryAndKeepsLegacyScalarJsonCompatible() {
        PlatformTypeDefinition geometry = PlatformTypeDefinition.geometry(new GeometryTypeDefinition(
                GeometryKind.MULTIPOLYGON,
                CrsReference.epsg(4490),
                CoordinateDimension.XY
        ));

        String json = objectMapper.writeValueAsString(geometry);
        assertEquals(geometry, objectMapper.readValue(json, PlatformTypeDefinition.class));

        PlatformTypeDefinition legacy = objectMapper.readValue(
                """
                {"type":"DECIMAL","length":null,"precision":18,"scale":4}
                """,
                PlatformTypeDefinition.class
        );
        assertEquals(PlatformTypeDefinition.decimal(18, 4), legacy);
        assertNull(legacy.geometry());
    }

    @Test
    void rejectsGeometryAsASqlServiceParameter() {
        PlatformTypeDefinition geometry = PlatformTypeDefinition.geometry(new GeometryTypeDefinition(
                GeometryKind.POINT,
                CrsReference.epsg(4326),
                CoordinateDimension.XY
        ));

        assertThrows(
                IllegalArgumentException.class,
                () -> new SqlServiceParameterDefinition("shape", geometry, false, null)
        );
    }
}
