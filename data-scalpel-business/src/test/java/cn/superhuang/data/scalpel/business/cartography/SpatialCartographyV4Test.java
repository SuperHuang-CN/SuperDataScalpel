package cn.superhuang.data.scalpel.business.cartography;

import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument;
import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument.ClassBreakVisualChannel;
import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument.ClassBreaksRenderer;
import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument.ClassificationMethod;
import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument.ColorRamp;
import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument.GeometryFamily;
import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument.LineSymbol;
import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument.NumericRange;
import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument.PointSymbol;
import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument.PolygonSymbol;
import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument.StyleRule;
import cn.superhuang.data.scalpel.business.cartography.sld.SpatialSldCompiler;
import cn.superhuang.data.scalpel.business.cartography.validation.SpatialStyleDocumentValidator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpatialCartographyV4Test {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SpatialStyleDocumentValidator validator = new SpatialStyleDocumentValidator();

    @Test
    void serializesAndDeserializesDiscriminatedSymbols() throws Exception {
        SpatialStyleDocument source = SpatialStyleDocument.defaults(GeometryFamily.POINT);

        String json = objectMapper.writeValueAsString(source);
        SpatialStyleDocument restored = objectMapper.readValue(json, SpatialStyleDocument.class);

        assertThat(json).contains("\"schemaVersion\":4", "\"type\":\"SINGLE_SYMBOL\"", "\"type\":\"POINT\"");
        assertThat(restored.renderer()).isInstanceOf(SpatialStyleDocument.SingleSymbolRenderer.class);
        assertThat(((SpatialStyleDocument.SingleSymbolRenderer) restored.renderer()).symbol())
                .isInstanceOf(PointSymbol.class);
    }

    @Test
    void rejectsSymbolAndVisualChannelThatDoNotMatchGeometry() {
        SpatialStyleDocument wrongSymbol = new SpatialStyleDocument(4,
                new SpatialStyleDocument.SingleSymbolRenderer(LineSymbol.defaults()),
                SpatialStyleDocument.Labeling.disabled(), null);
        SpatialStyleDocument wrongChannel = classBreakDocument(
                ClassBreakVisualChannel.WIDTH,
                new NumericRange(1d, 8d),
                List.of(new StyleRule(UUID.randomUUID().toString(), "低", PolygonSymbol.defaults()),
                        new StyleRule(UUID.randomUUID().toString(), "高", PolygonSymbol.defaults()))
        );

        assertThatThrownBy(() -> validator.validate(wrongSymbol, GeometryFamily.POINT, List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("PointSymbol");
        assertThatThrownBy(() -> validator.validate(wrongChannel, GeometryFamily.POLYGON, numericFields()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("不支持分级表达方式");
    }

    @Test
    void compilesMaterializedPointSizesIntoClassBreakRules() {
        List<StyleRule> rules = List.of(
                new StyleRule(UUID.randomUUID().toString(), "小", withSize(6d)),
                new StyleRule(UUID.randomUUID().toString(), "中", withSize(15d)),
                new StyleRule(UUID.randomUUID().toString(), "大", withSize(24d))
        );
        SpatialStyleDocument input = new SpatialStyleDocument(4,
                new ClassBreaksRenderer("population", ClassificationMethod.EQUAL_INTERVAL,
                        ClassBreakVisualChannel.SIZE, null, new NumericRange(6d, 24d),
                        List.of("100", "200"), rules, null), SpatialStyleDocument.Labeling.disabled(), null);
        SpatialStyleDocument normalized = validator.validate(input, GeometryFamily.POINT, numericFields());

        String sld = new SpatialSldCompiler().compile("svc_places", GeometryFamily.POINT, normalized);

        assertThat(sld).contains("<sld:Size>6</sld:Size>", "<sld:Size>15</sld:Size>",
                "<sld:Size>24</sld:Size>", "PropertyIsLessThanOrEqualTo", "PropertyIsGreaterThan");
    }

    @Test
    void rejectsInvalidMagnitudeRange() {
        SpatialStyleDocument input = new SpatialStyleDocument(4,
                new ClassBreaksRenderer("population", ClassificationMethod.EQUAL_INTERVAL,
                        ClassBreakVisualChannel.SIZE, null, new NumericRange(24d, 6d),
                        List.of("100"), List.of(
                                new StyleRule(UUID.randomUUID().toString(), "小", withSize(6d)),
                                new StyleRule(UUID.randomUUID().toString(), "大", withSize(24d))
                        ), null), SpatialStyleDocument.Labeling.disabled(), null);

        assertThatThrownBy(() -> validator.validate(input, GeometryFamily.POINT, numericFields()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("最小值必须小于最大值");
    }

    private static SpatialStyleDocument classBreakDocument(
            ClassBreakVisualChannel channel, NumericRange range, List<StyleRule> rules
    ) {
        return new SpatialStyleDocument(4,
                new ClassBreaksRenderer("population", ClassificationMethod.EQUAL_INTERVAL, channel,
                        new ColorRamp("BLUE_PURPLE", false), range, List.of("100"), rules, null),
                SpatialStyleDocument.Labeling.disabled(), null);
    }

    private static PointSymbol withSize(double size) {
        PointSymbol defaults = PointSymbol.defaults();
        return new PointSymbol(defaults.shape(), size, defaults.fillColor(), defaults.fillOpacity(),
                defaults.outlineColor(), defaults.outlineOpacity(), defaults.outlineWidth());
    }

    private static List<SpatialStyleDocument.Field> numericFields() {
        return List.of(new SpatialStyleDocument.Field("population", "人口", "LONG",
                SpatialStyleDocument.ValueType.NUMBER, true, true, true));
    }

    @Test
    void rejectsLegacySchemaAndDisjointOrNonFiniteScales() {
        var defaults = SpatialStyleDocument.defaults(GeometryFamily.POINT);
        assertThatThrownBy(() -> validator.validate(new SpatialStyleDocument(3, defaults.renderer(), defaults.labeling(), null), GeometryFamily.POINT, List.of()))
                .hasMessageContaining("版本");
        assertThatThrownBy(() -> validator.validate(new SpatialStyleDocument(4, defaults.renderer(), defaults.labeling(), new SpatialStyleDocument.ScaleRange(Double.NaN, null)), GeometryFamily.POINT, List.of()))
                .hasMessageContaining("有限正数");
        assertThatThrownBy(() -> validator.validate(new SpatialStyleDocument(4, defaults.renderer(), labels(new SpatialStyleDocument.ScaleRange(100d, 500d)), new SpatialStyleDocument.ScaleRange(500d, 1000d)), GeometryFamily.POINT, numericFields()))
                .hasMessageContaining("交集");
    }

    @Test
    void unlimitedAndOneSidedScalesDoNotUnboxNullBounds() {
        var unlimited = SpatialStyleDocument.ScaleRange.unlimited();
        var minimum = new SpatialStyleDocument.ScaleRange(100d, null);
        var maximum = new SpatialStyleDocument.ScaleRange(null, 500d);
        assertThat(unlimited.intersect(unlimited)).isEqualTo(unlimited);
        assertThat(unlimited.intersect(minimum)).isEqualTo(minimum);
        assertThat(maximum.intersect(unlimited)).isEqualTo(maximum);
        assertThat(minimum.intersect(maximum)).isEqualTo(new SpatialStyleDocument.ScaleRange(100d, 500d));
    }

    @Test
    void compilesSeparateLabelPassWithIntersectedScaleAndEscapedFormatting() throws Exception {
        var input = new SpatialStyleDocument(4, SpatialStyleDocument.defaults(GeometryFamily.POINT).renderer(),
                labels(new SpatialStyleDocument.ScaleRange(2000d, 50000d)), new SpatialStyleDocument.ScaleRange(1000d, 20000d));
        String sld = new SpatialSldCompiler().compile("svc_test", GeometryFamily.POINT, validator.validate(input, GeometryFamily.POINT, numericFields()));
        var xml = parse(sld);
        var styles = xml.getElementsByTagNameNS(SpatialSldCompiler.SLD_NAMESPACE, "FeatureTypeStyle");
        assertThat(styles.getLength()).isEqualTo(2);
        assertThat(styles.item(0).getTextContent()).contains("1000", "20000");
        assertThat(styles.item(1).getTextContent()).contains("2000", "20000", "人口<&", "单位", "0.00");
        assertThat(sld).contains("PropertyIsNull", "PropertyIsNotEqualTo", "numberFormat", "DisplacementX>-6", "AnchorPointX>1");
        assertThat(objectMapper.readValue(objectMapper.writeValueAsString(input), SpatialStyleDocument.class)).isEqualTo(input);
    }

    @Test
    void lineWidthMaterializationKeepsCasingAndDrawsAllCasingsFirst() throws Exception {
        var casing = new SpatialStyleDocument.LineCasing("#FFFFFF", 0.7d, 2d);
        var line = new LineSymbol("#445566", 0.9d, 2d, SpatialStyleDocument.LinePattern.DASHED, casing);
        var input = classBreakDocument(ClassBreakVisualChannel.WIDTH, new NumericRange(1d, 8d),
                List.of(new StyleRule(UUID.randomUUID().toString(), "低", line), new StyleRule(UUID.randomUUID().toString(), "高", line)));
        var normalized = validator.validate(input, GeometryFamily.LINE, numericFields());
        assertThat(((LineSymbol) ((ClassBreaksRenderer) normalized.renderer()).classBreakRules().getFirst().symbol()).casing()).isEqualTo(casing);
        var xml = parse(new SpatialSldCompiler().compile("svc_lines", GeometryFamily.LINE, normalized));
        var styles = xml.getElementsByTagNameNS(SpatialSldCompiler.SLD_NAMESPACE, "FeatureTypeStyle");
        assertThat(styles.getLength()).isEqualTo(2);
        assertThat(styles.item(0).getTextContent()).contains("#FFFFFF", "12");
        assertThat(styles.item(1).getTextContent()).contains("#445566", "8 4");
    }

    @Test
    void polygonPatternsUseOnlyInternalMarksAndKeepBackgroundAndBoundary() throws Exception {
        for (var type : SpatialStyleDocument.FillPattern.values()) {
            var pattern = new SpatialStyleDocument.PolygonPattern(type, "#123456", 0.8d, 12d, 1d, 2d);
            var symbol = new PolygonSymbol("#AABBCC", 0.3d, "#445566", 1d, 2d, SpatialStyleDocument.LinePattern.SOLID, pattern);
            var input = new SpatialStyleDocument(4, new SpatialStyleDocument.SingleSymbolRenderer(symbol), null, null);
            var normalized = validator.validate(input, GeometryFamily.POLYGON, List.of());
            String sld = new SpatialSldCompiler().compile("svc_area", GeometryFamily.POLYGON, normalized);
            assertThat(parse(sld).getElementsByTagNameNS(SpatialSldCompiler.SLD_NAMESPACE, "PolygonSymbolizer").getLength()).isEqualTo(3);
            assertThat(sld).contains("GraphicFill", "#AABBCC", "#445566", "#123456").doesNotContain("ExternalGraphic", "OnlineResource");
            assertThat(objectMapper.readValue(objectMapper.writeValueAsString(normalized), SpatialStyleDocument.class)).isEqualTo(normalized);
        }
    }

    @Test
    void rejectsInvalidPatternAndCasingDimensions() {
        var line = new LineSymbol("#445566", 1d, 2d, SpatialStyleDocument.LinePattern.SOLID, new SpatialStyleDocument.LineCasing("#FFFFFF", 1d, 11d));
        assertThatThrownBy(() -> validator.validate(new SpatialStyleDocument(4, new SpatialStyleDocument.SingleSymbolRenderer(line), null, null), GeometryFamily.LINE, List.of()))
                .hasMessageContaining("外描边");
        var polygon = new PolygonSymbol("#FFFFFF", 1d, "#000000", 1d, 1d, SpatialStyleDocument.LinePattern.SOLID,
                new SpatialStyleDocument.PolygonPattern(SpatialStyleDocument.FillPattern.DOT, "#000000", 1d, 6d, 1d, 8d));
        assertThatThrownBy(() -> validator.validate(new SpatialStyleDocument(4, new SpatialStyleDocument.SingleSymbolRenderer(polygon), null, null), GeometryFamily.POLYGON, List.of()))
                .hasMessageContaining("点径");
    }

    @Test
    void uniqueValuesKeepValidComparisonAndElseFilterSeparateFromLabels() throws Exception {
        var renderer = new SpatialStyleDocument.UniqueValueRenderer("population", SpatialStyleDocument.ValueType.NUMBER,
                new ColorRamp("BLUES", false),
                List.of(new SpatialStyleDocument.UniqueValueRule(UUID.randomUUID().toString(), "10", "十", PointSymbol.defaults())),
                SpatialStyleDocument.NullHandling.OTHER, null, new StyleRule(UUID.randomUUID().toString(), "其他", PointSymbol.defaults()));
        var input = new SpatialStyleDocument(4, renderer, labels(null), null);
        var xml = parse(new SpatialSldCompiler().compile("svc_points", GeometryFamily.POINT,
                validator.validate(input, GeometryFamily.POINT, numericFields())));
        var comparison = (org.w3c.dom.Element) xml.getElementsByTagNameNS(SpatialSldCompiler.OGC_NAMESPACE, "PropertyIsEqualTo").item(0);
        assertThat(comparison.getElementsByTagNameNS(SpatialSldCompiler.OGC_NAMESPACE, "Literal").item(0).getTextContent()).isEqualTo("10");
        var styles = xml.getElementsByTagNameNS(SpatialSldCompiler.SLD_NAMESPACE, "FeatureTypeStyle");
        assertThat(styles.getLength()).isEqualTo(2);
        assertThat(((org.w3c.dom.Element) styles.item(0)).getElementsByTagNameNS(SpatialSldCompiler.SLD_NAMESPACE, "ElseFilter").getLength()).isEqualTo(1);
        assertThat(((org.w3c.dom.Element) styles.item(1)).getElementsByTagNameNS(SpatialSldCompiler.SLD_NAMESPACE, "ElseFilter").getLength()).isZero();
    }

    private static SpatialStyleDocument.Labeling labels(SpatialStyleDocument.ScaleRange scale) {
        return new SpatialStyleDocument.Labeling(true, "population", 12d, true, "#123456", "#FFFFFF", 1.5d,
                "人口<&", "单位", 2, SpatialStyleDocument.PointLabelPosition.LEFT, 6d,
                SpatialStyleDocument.LineLabelPlacement.FOLLOW_LINE, true, 300d, true, false, scale);
    }

    private static org.w3c.dom.Document parse(String sld) throws Exception {
        var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new org.xml.sax.InputSource(new java.io.StringReader(sld)));
    }
}
