package cn.superhuang.data.scalpel.business.cartography.sld;

import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument;
import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument.ClassBreaksRenderer;
import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument.GeometryFamily;
import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument.LineSymbol;
import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument.PointSymbol;
import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument.PolygonSymbol;
import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument.SingleSymbolRenderer;
import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument.SpatialSymbol;
import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument.StyleRule;
import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument.UniqueValueRenderer;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;

/** Compiles one normalized V4 cartography document into an SLD 1.0 document. */
public final class SpatialSldCompiler {

    public static final String SLD_NAMESPACE = "http://www.opengis.net/sld";
    public static final String OGC_NAMESPACE = "http://www.opengis.net/ogc";

    public String compile(String layerName, GeometryFamily family, SpatialStyleDocument style) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document document = factory.newDocumentBuilder().newDocument();
            Element root = element(document, "StyledLayerDescriptor");
            root.setAttribute("version", "1.0.0");
            root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:sld", SLD_NAMESPACE);
            root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:ogc", OGC_NAMESPACE);
            document.appendChild(root);
            Element namedLayer = append(document, root, "NamedLayer");
            text(document, namedLayer, "Name", layerName);
            Element userStyle = append(document, namedLayer, "UserStyle");
            text(document, userStyle, "Title", "DataScalpel cartography style");
            Element featureTypeStyle = append(document, userStyle, "FeatureTypeStyle");
            renderer(document, featureTypeStyle, family, style.renderer());
            applyScale(document, featureTypeStyle, style.scaleRange());
            if (family == GeometryFamily.LINE) casingLayer(document, userStyle, featureTypeStyle, style.renderer());
            if (style.labeling() != null && style.labeling().enabled()) {
                Element labels = append(document, userStyle, "FeatureTypeStyle");
                labeling(document, labels, family, style.labeling());
                applyScale(document, labels, style.scaleRange().intersect(style.labeling().scaleRange()));
            }
            return serialize(document);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("生成空间样式 SLD 失败", exception);
        }
    }

    private static void renderer(
            Document document, Element parent, GeometryFamily family, SpatialStyleDocument.Renderer renderer
    ) {
        switch (renderer) {
            case SingleSymbolRenderer single -> rule(document, parent, "单一符号", null, family, single.symbol());
            case UniqueValueRenderer unique -> unique(document, parent, family, unique);
            case ClassBreaksRenderer classBreaks -> classBreaks(document, parent, family, classBreaks);
        }
    }

    private static void unique(Document document, Element parent, GeometryFamily family, UniqueValueRenderer renderer) {
        for (SpatialStyleDocument.UniqueValueRule item : renderer.uniqueValueRules()) {
            Element filter = comparison(document, "PropertyIsEqualTo", renderer.fieldCode(), item.value());
            rule(document, parent, item.label(), filter, family, item.symbol());
        }
        if (renderer.nullHandling() == SpatialStyleDocument.NullHandling.SEPARATE && renderer.nullRule() != null) {
            Element filter = filter(document, "PropertyIsNull");
            property(document, filter, renderer.fieldCode());
            rule(document, parent, renderer.nullRule().label(), filter, family, renderer.nullRule().symbol());
        }
        if (renderer.elseRule() != null) {
            rule(document, parent, renderer.elseRule().label(), element(document, "ElseFilter"), family,
                    renderer.elseRule().symbol());
        }
    }

    private static void classBreaks(
            Document document, Element parent, GeometryFamily family, ClassBreaksRenderer renderer
    ) {
        List<String> breaks = renderer.breaks();
        List<StyleRule> rules = renderer.classBreakRules();
        for (int index = 0; index < rules.size(); index++) {
            Element filter;
            if (breaks.isEmpty()) {
                filter = ogc(document, "Filter");
                Element not = ogc(document, "Not");
                Element isNull = ogc(document, "PropertyIsNull");
                property(document, isNull, renderer.fieldCode());
                not.appendChild(isNull);
                filter.appendChild(not);
            } else if (index == 0) {
                filter = comparison(document, "PropertyIsLessThanOrEqualTo", renderer.fieldCode(), breaks.getFirst());
            } else if (index == rules.size() - 1) {
                filter = comparison(document, "PropertyIsGreaterThan", renderer.fieldCode(), breaks.getLast());
            } else {
                Element and = ogc(document, "And");
                and.appendChild(comparisonExpression(document, "PropertyIsGreaterThan",
                        renderer.fieldCode(), breaks.get(index - 1)));
                and.appendChild(comparisonExpression(document, "PropertyIsLessThanOrEqualTo",
                        renderer.fieldCode(), breaks.get(index)));
                filter = ogc(document, "Filter");
                filter.appendChild(and);
            }
            StyleRule item = rules.get(index);
            rule(document, parent, item.label(), filter, family, item.symbol());
        }
        if (renderer.nullRule() != null) {
            Element filter = filter(document, "PropertyIsNull");
            property(document, filter, renderer.fieldCode());
            rule(document, parent, renderer.nullRule().label(), filter, family, renderer.nullRule().symbol());
        }
    }

    private static Element comparison(Document document, String operator, String field, String value) {
        Element filter = ogc(document, "Filter");
        filter.appendChild(comparisonExpression(document, operator, field, value));
        return filter;
    }

    private static Element comparisonExpression(Document document, String operator, String field, String value) {
        Element expression = ogc(document, operator);
        property(document, expression, field);
        ogcText(document, expression, "Literal", value);
        return expression;
    }

    private static Element filter(Document document, String expressionName) {
        Element filter = ogc(document, "Filter");
        filter.appendChild(ogc(document, expressionName));
        return filter;
    }

    private static void property(Document document, Element parent, String field) {
        Element expression = "Filter".equals(parent.getLocalName()) && parent.getFirstChild() instanceof Element child
                ? child : parent;
        ogcText(document, expression, "PropertyName", field);
    }

    private static void rule(Document document, Element parent, String title, Element filter,
                             GeometryFamily family, SpatialSymbol symbol) {
        Element rule = append(document, parent, "Rule");
        text(document, rule, "Title", title);
        if (filter != null) rule.appendChild(filter);
        switch (family) {
            case POINT -> {
                if (!(symbol instanceof PointSymbol point)) throw symbolMismatch(family);
                point(document, rule, point);
            }
            case LINE -> {
                if (!(symbol instanceof LineSymbol line)) throw symbolMismatch(family);
                line(document, rule, line);
            }
            case POLYGON -> {
                if (!(symbol instanceof PolygonSymbol polygon)) throw symbolMismatch(family);
                polygon(document, rule, polygon);
            }
            case GENERIC -> throw new IllegalArgumentException("通用 Geometry 不支持可视化制图");
        }
    }

    private static void point(Document document, Element rule, PointSymbol symbol) {
        Element symbolizer = append(document, rule, "PointSymbolizer");
        Element graphic = append(document, symbolizer, "Graphic");
        Element mark = append(document, graphic, "Mark");
        text(document, mark, "WellKnownName", symbol.shape().name().toLowerCase(Locale.ROOT));
        fill(document, mark, symbol.fillColor(), symbol.fillOpacity());
        stroke(document, mark, symbol.outlineColor(), symbol.outlineOpacity(), symbol.outlineWidth(),
                SpatialStyleDocument.LinePattern.SOLID);
        text(document, graphic, "Size", decimal(symbol.size()));
    }

    private static void line(Document document, Element rule, LineSymbol symbol) {
        Element symbolizer = append(document, rule, "LineSymbolizer");
        stroke(document, symbolizer, symbol.color(), symbol.opacity(), symbol.width(), symbol.pattern());
    }

    private static void polygon(Document document, Element rule, PolygonSymbol symbol) {
        Element symbolizer = append(document, rule, "PolygonSymbolizer");
        fill(document, symbolizer, symbol.fillColor(), symbol.fillOpacity());
        if (symbol.pattern() != null) {
            SpatialStyleDocument.PolygonPattern pattern = symbol.pattern();
            Element overlay = append(document, rule, "PolygonSymbolizer");
            Element graphicFill = append(document, append(document, overlay, "Fill"), "GraphicFill");
            Element graphic = append(document, graphicFill, "Graphic");
            Element mark = append(document, graphic, "Mark");
            text(document, mark, "WellKnownName", switch (pattern.type()) {
                case DIAGONAL -> "shape://slash";
                case CROSS -> "shape://times";
                case DOT -> "circle";
            });
            if (pattern.type() == SpatialStyleDocument.FillPattern.DOT) {
                fill(document, mark, pattern.color(), 1d);
            } else stroke(document, mark, pattern.color(), 1d, pattern.strokeWidth(), SpatialStyleDocument.LinePattern.SOLID);
            text(document, graphic, "Opacity", decimal(pattern.opacity()));
            text(document, graphic, "Size", decimal(pattern.type() == SpatialStyleDocument.FillPattern.DOT ? pattern.dotSize() : pattern.spacing()));
            if (pattern.type() == SpatialStyleDocument.FillPattern.DOT) {
                vendor(document, overlay, "graphic-margin", decimal((pattern.spacing() - pattern.dotSize()) / 2d));
            }
            symbolizer = append(document, rule, "PolygonSymbolizer");
        }
        stroke(document, symbolizer, symbol.outlineColor(), symbol.outlineOpacity(), symbol.outlineWidth(),
                symbol.outlinePattern());
    }

    private static void applyScale(Document document, Element style, SpatialStyleDocument.ScaleRange range) {
        for (int i = 0; i < style.getChildNodes().getLength(); i++) {
            if (!(style.getChildNodes().item(i) instanceof Element rule) || !"Rule".equals(rule.getLocalName())) continue;
            org.w3c.dom.Node before = null;
            for (int j = 0; j < rule.getChildNodes().getLength(); j++) {
                if (rule.getChildNodes().item(j) instanceof Element element && element.getLocalName().endsWith("Symbolizer")) {
                    before = element;
                    break;
                }
            }
            if (range.minScaleDenominator() != null) {
                Element min = element(document, "MinScaleDenominator"); min.setTextContent(decimal(range.minScaleDenominator()));
                rule.insertBefore(min, before);
            }
            if (range.maxScaleDenominator() != null) {
                Element max = element(document, "MaxScaleDenominator"); max.setTextContent(decimal(range.maxScaleDenominator()));
                rule.insertBefore(max, before);
            }
        }
    }

    /** Separate feature-type-style pass keeps casings below every foreground line, including crossings. */
    private static void casingLayer(Document document, Element userStyle, Element foreground, SpatialStyleDocument.Renderer renderer) {
        List<SpatialSymbol> symbols = new ArrayList<>();
        switch (renderer) {
            case SingleSymbolRenderer single -> symbols.add(single.symbol());
            case UniqueValueRenderer unique -> {
                unique.uniqueValueRules().forEach(rule -> symbols.add(rule.symbol()));
                if (unique.nullHandling() == SpatialStyleDocument.NullHandling.SEPARATE && unique.nullRule() != null) symbols.add(unique.nullRule().symbol());
                if (unique.elseRule() != null) symbols.add(unique.elseRule().symbol());
            }
            case ClassBreaksRenderer breaks -> {
                breaks.classBreakRules().forEach(rule -> symbols.add(rule.symbol()));
                if (breaks.nullRule() != null) symbols.add(breaks.nullRule().symbol());
            }
        }
        if (symbols.stream().noneMatch(symbol -> ((LineSymbol) symbol).casing() != null)) return;
        Element background = (Element) foreground.cloneNode(true);
        var rules = background.getElementsByTagNameNS(SLD_NAMESPACE, "Rule");
        for (int i = 0; i < rules.getLength(); i++) {
            Element rule = (Element) rules.item(i);
            rule.removeChild(rule.getElementsByTagNameNS(SLD_NAMESPACE, "LineSymbolizer").item(0));
            LineSymbol symbol = (LineSymbol) symbols.get(i);
            var casing = symbol.casing();
            // Keep non-casing rules transparent so the pass preserves ElseFilter exclusions.
            stroke(document, append(document, rule, "LineSymbolizer"), casing == null ? symbol.color() : casing.color(),
                    casing == null ? 0d : casing.opacity(), casing == null ? symbol.width() : symbol.width() + 2 * casing.width(),
                    SpatialStyleDocument.LinePattern.SOLID);
        }
        userStyle.insertBefore(background, foreground);
    }

    private static IllegalArgumentException symbolMismatch(GeometryFamily family) {
        return new IllegalArgumentException("符号类型与 Geometry 类型不一致：" + family);
    }

    private static void labeling(Document document, Element parent, GeometryFamily family,
                                 SpatialStyleDocument.Labeling labeling) {
        Element rule = append(document, parent, "Rule");
        text(document, rule, "Title", "文字标注");
        Element filter = ogc(document, "Filter");
        Element and = ogc(document, "And");
        Element not = ogc(document, "Not");
        Element isNull = ogc(document, "PropertyIsNull");
        property(document, isNull, labeling.fieldCode()); not.appendChild(isNull); and.appendChild(not);
        Element nonEmpty = ogc(document, "PropertyIsNotEqualTo");
        Element asText = function(document, nonEmpty, "strConcat");
        ogcText(document, asText, "Literal", ""); property(document, asText, labeling.fieldCode());
        ogcText(document, nonEmpty, "Literal", ""); and.appendChild(nonEmpty); filter.appendChild(and); rule.appendChild(filter);
        Element symbolizer = append(document, rule, "TextSymbolizer");
        Element label = append(document, symbolizer, "Label");
        Element concat = function(document, label, "Concatenate");
        ogcText(document, concat, "Literal", labeling.prefix());
        if (labeling.decimalPlaces() == null) ogcText(document, concat, "PropertyName", labeling.fieldCode());
        else {
            Element format = function(document, concat, "numberFormat");
            ogcText(document, format, "Literal", labeling.decimalPlaces() == 0 ? "0" : "0." + "0".repeat(labeling.decimalPlaces()));
            ogcText(document, format, "PropertyName", labeling.fieldCode());
        }
        ogcText(document, concat, "Literal", labeling.suffix());
        Element font = append(document, symbolizer, "Font");
        css(document, font, "font-family", "SansSerif");
        css(document, font, "font-size", decimal(labeling.fontSize()));
        css(document, font, "font-style", "normal");
        css(document, font, "font-weight", Boolean.TRUE.equals(labeling.bold()) ? "bold" : "normal");
        if (family == GeometryFamily.LINE && labeling.linePlacement() == SpatialStyleDocument.LineLabelPlacement.FOLLOW_LINE) {
            Element placement = append(document, symbolizer, "LabelPlacement");
            append(document, placement, "LinePlacement");
        } else {
            Element placement = append(document, symbolizer, "LabelPlacement");
            Element pointPlacement = append(document, placement, "PointPlacement");
            if (family == GeometryFamily.LINE) text(document, pointPlacement, "Rotation", "0");
            if (family == GeometryFamily.POINT) {
                Element anchor = append(document, pointPlacement, "AnchorPoint");
                text(document, anchor, "AnchorPointX", switch (labeling.pointPosition()) { case LEFT -> "1"; case RIGHT -> "0"; default -> "0.5"; });
                text(document, anchor, "AnchorPointY", switch (labeling.pointPosition()) { case TOP -> "0"; case BOTTOM -> "1"; default -> "0.5"; });
                Element displacement = append(document, pointPlacement, "Displacement");
                text(document, displacement, "DisplacementX", decimal(switch (labeling.pointPosition()) { case LEFT -> -labeling.pointOffset(); case RIGHT -> labeling.pointOffset(); default -> 0d; }));
                text(document, displacement, "DisplacementY", decimal(switch (labeling.pointPosition()) { case TOP -> labeling.pointOffset(); case BOTTOM -> -labeling.pointOffset(); default -> 0d; }));
            }
        }
        Element halo = append(document, symbolizer, "Halo");
        text(document, halo, "Radius", decimal(labeling.haloWidth()));
        fill(document, halo, labeling.haloColor(), 1d);
        fill(document, symbolizer, labeling.color(), 1d);
        vendor(document, symbolizer, "conflictResolution", Boolean.toString(!labeling.allowOverlap()));
        if (family == GeometryFamily.LINE) {
            vendor(document, symbolizer, "followLine", Boolean.toString(labeling.linePlacement() == SpatialStyleDocument.LineLabelPlacement.FOLLOW_LINE));
            vendor(document, symbolizer, "maxDisplacement", "200");
            vendor(document, symbolizer, "repeat", labeling.repeat() ? decimal(labeling.repeatDistance()) : "0");
        }
        if (family == GeometryFamily.POLYGON) {
            vendor(document, symbolizer, "polygonAlign", "manual");
            vendor(document, symbolizer, "goodnessOfFit", labeling.polygonFit() ? "1" : "0");
        }
    }

    private static Element function(Document document, Element parent, String name) {
        Element function = ogc(document, "Function"); function.setAttribute("name", name); parent.appendChild(function); return function;
    }

    private static void fill(Document document, Element parent, String color, double opacity) {
        Element fill = append(document, parent, "Fill");
        css(document, fill, "fill", color);
        css(document, fill, "fill-opacity", decimal(opacity));
    }

    private static void stroke(Document document, Element parent, String color, double opacity, double width,
                               SpatialStyleDocument.LinePattern pattern) {
        Element stroke = append(document, parent, "Stroke");
        css(document, stroke, "stroke", color);
        css(document, stroke, "stroke-opacity", decimal(opacity));
        css(document, stroke, "stroke-width", decimal(width));
        String dashArray = switch (pattern) {
            case SOLID -> null;
            case DASHED -> "8 4";
            case DOTTED -> "2 3";
        };
        if (dashArray != null) css(document, stroke, "stroke-dasharray", dashArray);
    }

    private static void css(Document document, Element parent, String name, String value) {
        Element parameter = append(document, parent, "CssParameter");
        parameter.setAttribute("name", name);
        parameter.setTextContent(value);
    }

    private static void vendor(Document document, Element parent, String name, String value) {
        Element option = append(document, parent, "VendorOption");
        option.setAttribute("name", name);
        option.setTextContent(value);
    }

    private static Element element(Document document, String name) {
        return document.createElementNS(SLD_NAMESPACE, "sld:" + name);
    }

    private static Element ogc(Document document, String name) {
        return document.createElementNS(OGC_NAMESPACE, "ogc:" + name);
    }

    private static Element append(Document document, Element parent, String name) {
        Element child = element(document, name);
        parent.appendChild(child);
        return child;
    }

    private static void text(Document document, Element parent, String name, String value) {
        Element child = append(document, parent, name);
        child.setTextContent(value);
    }

    private static void ogcText(Document document, Element parent, String name, String value) {
        Element child = ogc(document, name);
        child.setTextContent(value);
        parent.appendChild(child);
    }

    private static String decimal(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("SLD 数值必须是有限数字");
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static String serialize(Document document) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }
}
