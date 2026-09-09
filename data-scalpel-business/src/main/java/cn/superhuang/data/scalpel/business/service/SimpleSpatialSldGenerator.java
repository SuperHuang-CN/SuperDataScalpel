package cn.superhuang.data.scalpel.business.service;

import cn.superhuang.data.scalpel.business.service.domain.SimpleSpatialStyle;
import cn.superhuang.data.scalpel.business.service.domain.SpatialGeometryFamily;
import cn.superhuang.data.scalpel.business.service.domain.SpatialLinePattern;
import org.springframework.stereotype.Component;
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
import java.util.Locale;

/** Generates a compact SLD 1.0 document without interpolating values into XML text. */
@Component
public class SimpleSpatialSldGenerator {

    static final String SLD_NAMESPACE = "http://www.opengis.net/sld";
    private static final String OGC_NAMESPACE = "http://www.opengis.net/ogc";

    public String generate(String layerName, SpatialGeometryFamily family, SimpleSpatialStyle input) {
        try {
            SimpleSpatialStyle style = input.normalized(family);
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
            text(document, userStyle, "Title", "DataScalpel simple style");
            Element featureTypeStyle = append(document, userStyle, "FeatureTypeStyle");
            Element rule = append(document, featureTypeStyle, "Rule");
            switch (family) {
                case POINT -> point(document, rule, style);
                case LINE -> line(document, rule, style);
                case POLYGON -> polygon(document, rule, style);
                case GENERIC -> throw new IllegalArgumentException("通用 Geometry 不支持简单 SLD");
            }
            return serialize(document);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("生成简单 SLD 失败", exception);
        }
    }

    private static void point(Document document, Element rule, SimpleSpatialStyle style) {
        Element symbolizer = append(document, rule, "PointSymbolizer");
        Element graphic = append(document, symbolizer, "Graphic");
        Element mark = append(document, graphic, "Mark");
        text(document, mark, "WellKnownName", style.markerShape().name().toLowerCase(Locale.ROOT));
        fill(document, mark, style.fillColor(), style.fillOpacity());
        stroke(document, mark, style.strokeColor(), style.strokeOpacity(), style.strokeWidth(), SpatialLinePattern.SOLID);
        text(document, graphic, "Size", decimal(style.pointSize()));
    }

    private static void line(Document document, Element rule, SimpleSpatialStyle style) {
        Element symbolizer = append(document, rule, "LineSymbolizer");
        stroke(document, symbolizer, style.strokeColor(), style.strokeOpacity(), style.strokeWidth(), style.linePattern());
    }

    private static void polygon(Document document, Element rule, SimpleSpatialStyle style) {
        Element symbolizer = append(document, rule, "PolygonSymbolizer");
        fill(document, symbolizer, style.fillColor(), style.fillOpacity());
        stroke(document, symbolizer, style.strokeColor(), style.strokeOpacity(), style.strokeWidth(), style.linePattern());
    }

    private static void fill(Document document, Element parent, String color, double opacity) {
        Element fill = append(document, parent, "Fill");
        css(document, fill, "fill", color);
        css(document, fill, "fill-opacity", decimal(opacity));
    }

    private static void stroke(
            Document document,
            Element parent,
            String color,
            double opacity,
            double width,
            SpatialLinePattern pattern
    ) {
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

    private static Element element(Document document, String name) {
        return document.createElementNS(SLD_NAMESPACE, "sld:" + name);
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

    private static String decimal(double value) {
        return Double.toString(value);
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
