package cn.superhuang.data.scalpel.business.service;

import cn.superhuang.data.scalpel.business.service.domain.SimpleSpatialStyle;
import cn.superhuang.data.scalpel.business.service.domain.SpatialGeometryFamily;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpatialSldTest {

    private final SimpleSpatialSldGenerator generator = new SimpleSpatialSldGenerator();
    private final SpatialSldValidator validator = new SpatialSldValidator();

    @Test
    void generatesAndValidatesDefaultPointStyle() {
        String sld = generator.generate(
                "svc_places", SpatialGeometryFamily.POINT,
                SimpleSpatialStyle.defaults(SpatialGeometryFamily.POINT)
        );

        SpatialSldValidator.ValidatedSld validated = validator.validate(
                "places.sld", sld.getBytes(StandardCharsets.UTF_8), SpatialGeometryFamily.POINT
        );

        assertThat(validated.text()).contains("PointSymbolizer", "#4F6BFF", "circle");
    }

    @Test
    void rejectsDoctypeAndExternalResources() {
        String xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE sld [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <StyledLayerDescriptor xmlns="http://www.opengis.net/sld" version="1.0.0">
                  <NamedLayer><Name>&xxe;</Name></NamedLayer>
                </StyledLayerDescriptor>
                """;
        String externalGraphic = """
                <StyledLayerDescriptor xmlns="http://www.opengis.net/sld" version="1.0.0">
                  <NamedLayer><UserStyle><FeatureTypeStyle><Rule><PointSymbolizer><Graphic>
                    <ExternalGraphic><OnlineResource href="https://example.invalid/icon.png"/></ExternalGraphic>
                  </Graphic></PointSymbolizer></Rule></FeatureTypeStyle></UserStyle></NamedLayer>
                </StyledLayerDescriptor>
                """;

        assertThatThrownBy(() -> validator.validate(
                "xxe.sld", xxe.getBytes(StandardCharsets.UTF_8), SpatialGeometryFamily.POINT
        )).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> validator.validate(
                "external.sld", externalGraphic.getBytes(StandardCharsets.UTF_8), SpatialGeometryFamily.POINT
        )).isInstanceOf(ResponseStatusException.class).hasMessageContaining("外部");
    }

    @Test
    void rejectsGeometryMismatchAndOversizedFiles() {
        String point = generator.generate(
                "svc_places", SpatialGeometryFamily.POINT,
                SimpleSpatialStyle.defaults(SpatialGeometryFamily.POINT)
        );

        assertThatThrownBy(() -> validator.validate(
                "places.sld", point.getBytes(StandardCharsets.UTF_8), SpatialGeometryFamily.POLYGON
        )).isInstanceOf(ResponseStatusException.class).hasMessageContaining("Symbolizer");
        assertThatThrownBy(() -> validator.validate(
                "large.sld", new byte[SpatialSldValidator.MAX_BYTES + 1], SpatialGeometryFamily.POINT
        )).isInstanceOf(ResponseStatusException.class).hasMessageContaining("512KB");
    }
}
