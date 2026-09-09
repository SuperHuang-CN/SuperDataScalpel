package cn.superhuang.data.scalpel.business.cartography.sld;

import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument.GeometryFamily;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/** Framework-free security and structure validation for user supplied SLD 1.0 documents. */
public final class UploadedSldValidator {

    public static final int MAX_BYTES = 512 * 1024;
    private static final Set<String> FORBIDDEN_ELEMENTS = Set.of("ExternalGraphic", "OnlineResource");

    public ValidatedSld validate(String fileName, byte[] bytes, GeometryFamily family) {
        String normalizedFileName = normalizeFileName(fileName);
        if (bytes == null || bytes.length == 0) throw invalid("SLD 文件不能为空");
        if (bytes.length > MAX_BYTES) throw invalid("SLD 文件不能超过 512KB");
        String xml = decodeUtf8(bytes);
        Document document = parse(xml);
        validateDocument(document, family);
        return new ValidatedSld(normalizedFileName, xml, bytes.length);
    }

    /** Rewrites only the in-memory preview document. The persisted upload remains byte-for-byte unchanged. */
    public String forPreview(ValidatedSld validated, String layerName) {
        if (validated == null) throw invalid("SLD 文件不能为空");
        if (layerName == null || layerName.isBlank()) throw invalid("预览图层名称不能为空");
        Document document = parse(validated.text());
        NodeList namedLayers = document.getElementsByTagNameNS(SpatialSldCompiler.SLD_NAMESPACE, "NamedLayer");
        if (namedLayers.getLength() == 0) throw invalid("SLD 必须至少包含一个 NamedLayer");
        for (int index = 0; index < namedLayers.getLength(); index++) {
            Element namedLayer = (Element) namedLayers.item(index);
            Element name = directChild(namedLayer, "Name");
            if (name == null) {
                name = document.createElementNS(SpatialSldCompiler.SLD_NAMESPACE, "sld:Name");
                namedLayer.insertBefore(name, namedLayer.getFirstChild());
            }
            name.setTextContent(layerName.trim());
        }
        return serialize(document);
    }

    private static String normalizeFileName(String fileName) {
        String normalized = fileName == null ? "" : fileName.trim().replace('\\', '/');
        normalized = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (normalized.isBlank() || normalized.length() > 255
                || normalized.contains("\n") || normalized.contains("\r")) {
            throw invalid("SLD 文件名不合法");
        }
        String lowerName = normalized.toLowerCase(Locale.ROOT);
        if (!lowerName.endsWith(".sld") && !lowerName.endsWith(".xml")) {
            throw invalid("仅支持 .sld 或 .xml 文件");
        }
        return normalized;
    }

    private static String decodeUtf8(byte[] bytes) {
        try {
            String value = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            return value.startsWith("\uFEFF") ? value.substring(1) : value;
        } catch (CharacterCodingException exception) {
            throw invalid("SLD 文件必须使用 UTF-8 编码");
        }
    }

    private static Document parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception exception) {
            throw invalid("SLD XML 格式不合法");
        }
    }

    private static void validateDocument(Document document, GeometryFamily family) {
        Element root = document.getDocumentElement();
        if (root == null || !"StyledLayerDescriptor".equals(root.getLocalName())
                || !SpatialSldCompiler.SLD_NAMESPACE.equals(root.getNamespaceURI())
                || !"1.0.0".equals(root.getAttribute("version"))) {
            throw invalid("文件根元素必须是 SLD 1.0 StyledLayerDescriptor");
        }
        inspectSecurity(root);
        requireSymbolizer(root, family);
    }

    private static void inspectSecurity(Element root) {
        NodeList nodes = root.getElementsByTagNameNS("*", "*");
        for (int index = 0; index < nodes.getLength(); index++) {
            Node node = nodes.item(index);
            if (!(node instanceof Element element)) continue;
            if (FORBIDDEN_ELEMENTS.contains(element.getLocalName())) {
                throw invalid("SLD 不允许使用外部图片、远程资源或外部字体");
            }
            if (("CssParameter".equals(element.getLocalName()) || "SvgParameter".equals(element.getLocalName()))
                    && "font-family".equalsIgnoreCase(element.getAttribute("name"))
                    && element.getTextContent() != null
                    && element.getTextContent().matches("(?is).*?(url\\s*\\(|https?://|file:|ftp:).*")) {
                throw invalid("SLD 不允许引用外部字体");
            }
            for (int attributeIndex = 0; attributeIndex < element.getAttributes().getLength(); attributeIndex++) {
                String value = element.getAttributes().item(attributeIndex).getNodeValue();
                if (value != null && value.matches("(?i)^\\s*(https?|file|ftp|jar):.*")) {
                    throw invalid("SLD 不允许引用外部资源");
                }
            }
        }
    }

    private static void requireSymbolizer(Element root, GeometryFamily family) {
        boolean matches = switch (family) {
            case POINT -> has(root, "PointSymbolizer");
            case LINE -> has(root, "LineSymbolizer");
            case POLYGON -> has(root, "PolygonSymbolizer");
            case GENERIC -> has(root, "PointSymbolizer") || has(root, "LineSymbolizer")
                    || has(root, "PolygonSymbolizer");
        };
        if (!matches) throw invalid("SLD 中没有与当前 Geometry 类型匹配的 Symbolizer");
    }

    private static Element directChild(Element parent, String localName) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element
                    && SpatialSldCompiler.SLD_NAMESPACE.equals(element.getNamespaceURI())
                    && localName.equals(element.getLocalName())) return element;
        }
        return null;
    }

    private static boolean has(Element root, String localName) {
        return root.getElementsByTagNameNS("*", localName).getLength() > 0;
    }

    private static String serialize(Document document) {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return writer.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("生成预览 SLD 失败", exception);
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    public record ValidatedSld(String fileName, String text, int size) {
    }
}
