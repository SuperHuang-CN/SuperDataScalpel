package cn.superhuang.data.scalpel.business.datasource.service;

import cn.superhuang.data.scalpel.business.datasource.domain.SpatialServiceProtocol;
import cn.superhuang.data.scalpel.business.datasource.web.response.SpatialCatalogEntryResponse;
import cn.superhuang.data.scalpel.contract.httpapi.HttpApiContracts;
import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Small, explicit management-side client for ArcGIS REST and WFS discovery. */
@Component
public class SpatialServiceClient {

    private static final int MAX_RESPONSE_CHARS = 10 * 1024 * 1024;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    public SpatialServiceClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<SpatialCatalogEntryResponse> discover(
            SpatialServiceProtocol protocol,
            HttpApiContracts.RuntimeConnection connection,
            String parent
    ) {
        return switch (protocol) {
            case ARCGIS_REST -> discoverArcGis(connection, parent);
            case WFS -> discoverWfs(connection);
        };
    }

    public SpatialFeatureDefinition describe(
            SpatialServiceProtocol protocol,
            HttpApiContracts.RuntimeConnection connection,
            String remoteIdentifier,
            Integer requestedEpsg
    ) {
        return switch (protocol) {
            case ARCGIS_REST -> describeArcGis(connection, remoteIdentifier, requestedEpsg);
            case WFS -> describeWfs(connection, remoteIdentifier, requestedEpsg);
        };
    }

    public SpatialProbe probe(SpatialServiceProtocol protocol, HttpApiContracts.RuntimeConnection connection) {
        try {
            return switch (protocol) {
                case ARCGIS_REST -> {
                    JsonNode value = json(get(connection, withQuery(connection.configuration().baseUrl(), Map.of("f", "pjson"))));
                    if (value.has("error")) throw remote("ARCGIS_REMOTE_ERROR", arcGisError(value));
                    if (!value.has("currentVersion") && !value.has("folders") && !value.has("services")
                            && !value.has("layers") && !value.has("type")) {
                        throw remote("ARCGIS_INVALID_SERVICE", "响应不是 ArcGIS REST 服务描述");
                    }
                    yield new SpatialProbe("ArcGIS REST", value.path("currentVersion").asText(null));
                }
                case WFS -> {
                    String body = get(connection, wfsUrl(connection.configuration().baseUrl(), Map.of(
                            "service", "WFS", "request", "GetCapabilities")));
                    String version = capabilityVersion(body);
                    yield new SpatialProbe("OGC WFS", version);
                }
            };
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw remote("SPATIAL_SERVICE_CONNECTION_FAILED", safeMessage(exception));
        }
    }

    public SpatialPreview preview(
            SpatialServiceProtocol protocol,
            HttpApiContracts.RuntimeConnection connection,
            SpatialFeatureDefinition definition,
            int limit
    ) {
        return switch (protocol) {
            case ARCGIS_REST -> previewArcGis(connection, definition, limit);
            case WFS -> previewWfs(connection, definition, limit);
        };
    }

    private List<SpatialCatalogEntryResponse> discoverArcGis(
            HttpApiContracts.RuntimeConnection connection,
            String parent
    ) {
        String base = parent == null || parent.isBlank() ? connection.configuration().baseUrl() : resolve(connection, parent);
        JsonNode root = json(get(connection, withQuery(base, Map.of("f", "pjson"))));
        requireArcGisSuccess(root);
        List<SpatialCatalogEntryResponse> result = new ArrayList<>();
        for (JsonNode folder : root.path("folders")) {
            String path = folder.asText();
            result.add(new SpatialCatalogEntryResponse(SpatialServiceProtocol.ARCGIS_REST, path, path,
                    path, "FOLDER", false, null));
        }
        for (JsonNode service : root.path("services")) {
            String name = service.path("name").asText();
            String type = service.path("type").asText();
            if ("FeatureServer".equals(type) || "MapServer".equals(type)) {
                result.add(new SpatialCatalogEntryResponse(SpatialServiceProtocol.ARCGIS_REST,
                        name + "/" + type, name, type, "SERVICE", false, null));
            }
        }
        for (JsonNode layer : root.path("layers")) {
            int id = layer.path("id").asInt(-1);
            if (id >= 0) {
                String identifier = trimSlash(relative(base, connection.configuration().baseUrl())) + "/" + id;
                result.add(new SpatialCatalogEntryResponse(SpatialServiceProtocol.ARCGIS_REST, identifier,
                        layer.path("name").asText("Layer " + id), layer.path("name").asText(),
                        "LAYER", true, epsg(layer.path("extent").path("spatialReference"))));
            }
        }
        for (JsonNode table : root.path("tables")) {
            int id = table.path("id").asInt(-1);
            if (id >= 0) {
                String identifier = trimSlash(relative(base, connection.configuration().baseUrl())) + "/" + id;
                result.add(new SpatialCatalogEntryResponse(SpatialServiceProtocol.ARCGIS_REST, identifier,
                        table.path("name").asText("Table " + id), table.path("name").asText(),
                        "TABLE", true, null));
            }
        }
        return List.copyOf(result);
    }

    private List<SpatialCatalogEntryResponse> discoverWfs(HttpApiContracts.RuntimeConnection connection) {
        String body = get(connection, wfsUrl(connection.configuration().baseUrl(), Map.of(
                "service", "WFS", "request", "GetCapabilities")));
        try {
            XMLStreamReader reader = xml(body);
            List<SpatialCatalogEntryResponse> result = new ArrayList<>();
            String version = null;
            String name = null;
            String title = null;
            String crs = null;
            boolean featureType = false;
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String local = reader.getLocalName();
                    if ("WFS_Capabilities".equals(local)) version = reader.getAttributeValue(null, "version");
                    if ("FeatureType".equals(local)) { featureType = true; name = null; title = null; crs = null; }
                    else if (featureType && "Name".equals(local)) name = reader.getElementText().trim();
                    else if (featureType && "Title".equals(local)) title = reader.getElementText().trim();
                    else if (featureType && ("DefaultCRS".equals(local) || "DefaultSRS".equals(local))) crs = reader.getElementText().trim();
                } else if (event == XMLStreamConstants.END_ELEMENT && "FeatureType".equals(reader.getLocalName())) {
                    if (name != null && !name.isBlank()) {
                        result.add(new SpatialCatalogEntryResponse(SpatialServiceProtocol.WFS, name, name,
                                title == null || title.isBlank() ? name : title, "FEATURE_TYPE", true, epsg(crs)));
                    }
                    featureType = false;
                }
            }
            if (version == null) throw remote("WFS_INVALID_CAPABILITIES", "响应不是有效 WFS Capabilities");
            return List.copyOf(result);
        } catch (XMLStreamException exception) {
            throw remote("WFS_INVALID_CAPABILITIES", "无法解析 WFS Capabilities");
        }
    }

    private SpatialFeatureDefinition describeArcGis(
            HttpApiContracts.RuntimeConnection connection, String remoteIdentifier, Integer requestedEpsg
    ) {
        String layerUrl = resolve(connection, remoteIdentifier);
        JsonNode root = json(get(connection, withQuery(layerUrl, Map.of("f", "pjson"))));
        requireArcGisSuccess(root);
        Integer sourceEpsg = epsg(root.path("extent").path("spatialReference"));
        if (sourceEpsg == null) sourceEpsg = epsg(root.path("spatialReference"));
        Integer outputEpsg = requestedEpsg == null ? sourceEpsg : requestedEpsg;
        List<CanvasColumnSchema> fields = new ArrayList<>();
        for (JsonNode field : root.path("fields")) {
            CanvasColumnSchema mapped = arcGisField(field);
            if (mapped != null) fields.add(mapped);
        }
        String geometryField = root.path("geometryField").path("name").asText(null);
        GeometryKind geometryKind = arcGisGeometry(root.path("geometryType").asText(null));
        if (geometryKind != null) {
            if (outputEpsg == null) throw remote("ARCGIS_CRS_UNRESOLVED", "ArcGIS 图层未提供可用 EPSG，请在登记时指定输出 EPSG");
            fields.add(new CanvasColumnSchema(
                    geometryField == null || geometryField.isBlank() ? "geometry" : geometryField,
                    PlatformDataType.GEOMETRY, null, null, null, true, null, false, false, "Geometry",
                    new GeometryTypeDefinition(geometryKind, CrsReference.epsg(outputEpsg),
                            cn.superhuang.data.scalpel.contract.type.CoordinateDimension.XY)
            ));
        }
        if (fields.isEmpty()) throw remote("ARCGIS_SCHEMA_EMPTY", "ArcGIS 图层没有可用字段");
        return new SpatialFeatureDefinition(
                SpatialServiceProtocol.ARCGIS_REST, remoteIdentifier, root.path("name").asText(remoteIdentifier),
                geometryField, outputEpsg, root.path("objectIdField").asText(null), null,
                "ESRI_JSON", "XY", fields
        );
    }

    private SpatialPreview previewArcGis(
            HttpApiContracts.RuntimeConnection connection, SpatialFeatureDefinition definition, int limit
    ) {
        String queryUrl = resolve(connection, definition.remoteIdentifier()).replaceFirst("/+$", "") + "/query";
        JsonNode root = json(get(connection, withQuery(queryUrl, Map.of(
                "f", "json", "where", "1=1", "outFields", "*", "returnGeometry", "false",
                "resultRecordCount", Integer.toString(limit + 1)
        ))));
        requireArcGisSuccess(root);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (JsonNode feature : root.path("features")) {
            if (rows.size() == limit) break;
            rows.add(jsonObject(feature.path("attributes")));
        }
        return new SpatialPreview(List.copyOf(rows), root.path("exceededTransferLimit").asBoolean(false)
                || root.path("features").size() > limit);
    }

    private SpatialFeatureDefinition describeWfs(
            HttpApiContracts.RuntimeConnection connection, String featureType, Integer requestedEpsg
    ) {
        String capabilities = get(connection, wfsUrl(connection.configuration().baseUrl(), Map.of(
                "service", "WFS", "request", "GetCapabilities")));
        String version = capabilityVersion(capabilities);
        String schema = get(connection, wfsUrl(connection.configuration().baseUrl(), Map.of(
                "service", "WFS", "version", version, "request", "DescribeFeatureType", "typeNames", featureType)));
        List<CanvasColumnSchema> fields = parseWfsSchema(schema, requestedEpsg);
        if (fields.isEmpty()) throw remote("WFS_SCHEMA_EMPTY", "WFS FeatureType 没有可用字段");
        CanvasColumnSchema geometry = fields.stream().filter(field -> field.fieldType() == PlatformDataType.GEOMETRY)
                .findFirst().orElse(null);
        Integer epsg = geometry == null ? requestedEpsg : geometry.geometry().crs().code();
        return new SpatialFeatureDefinition(SpatialServiceProtocol.WFS, featureType, featureType,
                geometry == null ? null : geometry.name(), epsg, null, version, "AUTO", "AUTO", fields);
    }

    private SpatialPreview previewWfs(
            HttpApiContracts.RuntimeConnection connection, SpatialFeatureDefinition definition, int limit
    ) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("service", "WFS");
        parameters.put("version", definition.wfsVersion());
        parameters.put("request", "GetFeature");
        parameters.put(definition.wfsVersion().startsWith("2.") ? "typeNames" : "typeName", definition.remoteIdentifier());
        parameters.put(definition.wfsVersion().startsWith("2.") ? "count" : "maxFeatures", Integer.toString(limit + 1));
        parameters.put("outputFormat", "application/json");
        JsonNode root = json(get(connection, wfsUrl(connection.configuration().baseUrl(), parameters)));
        if (root.has("exceptions") || root.has("ExceptionReport")) {
            throw remote("WFS_REMOTE_ERROR", root.path("exceptions").asText("WFS 服务返回错误"));
        }
        if (!"FeatureCollection".equals(root.path("type").asText())) {
            throw remote("WFS_PREVIEW_FORMAT_UNSUPPORTED", "WFS 服务未返回 GeoJSON FeatureCollection；请确认支持 application/json 输出格式");
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (JsonNode feature : root.path("features")) {
            if (rows.size() == limit) break;
            rows.add(jsonObject(feature.path("properties")));
        }
        return new SpatialPreview(List.copyOf(rows), root.path("numberMatched").asLong(rows.size()) > limit
                || root.path("features").size() > limit);
    }

    private List<CanvasColumnSchema> parseWfsSchema(String schema, Integer requestedEpsg) {
        try {
            XMLStreamReader reader = xml(schema);
            List<CanvasColumnSchema> result = new ArrayList<>();
            while (reader.hasNext()) {
                if (reader.next() != XMLStreamConstants.START_ELEMENT || !"element".equals(reader.getLocalName())) continue;
                String name = reader.getAttributeValue(null, "name");
                String type = reader.getAttributeValue(null, "type");
                if (name == null || type == null || "boundedBy".equals(name)) continue;
                boolean nullable = "0".equals(reader.getAttributeValue(null, "minOccurs"));
                CanvasColumnSchema field = wfsField(name, type, nullable, requestedEpsg);
                if (field != null) result.add(field);
            }
            return List.copyOf(result);
        } catch (XMLStreamException exception) {
            throw remote("WFS_SCHEMA_INVALID", "无法解析 WFS DescribeFeatureType 响应");
        }
    }

    private static CanvasColumnSchema wfsField(String name, String type, boolean nullable, Integer epsg) {
        String normalized = type.toLowerCase(Locale.ROOT);
        GeometryKind geometry = normalized.contains("point") ? GeometryKind.POINT
                : normalized.contains("multipoint") ? GeometryKind.MULTIPOINT
                : normalized.contains("multicurve") || normalized.contains("multilinestring") ? GeometryKind.MULTILINESTRING
                : normalized.contains("curve") || normalized.contains("linestring") ? GeometryKind.LINESTRING
                : normalized.contains("multisurface") || normalized.contains("multipolygon") ? GeometryKind.MULTIPOLYGON
                : normalized.contains("surface") || normalized.contains("polygon") ? GeometryKind.POLYGON
                : normalized.contains("geometry") ? GeometryKind.GEOMETRY : null;
        if (geometry != null) {
            if (epsg == null) return null;
            return new CanvasColumnSchema(name, PlatformDataType.GEOMETRY, null, null, null, nullable,
                    null, false, false, "Geometry", new GeometryTypeDefinition(geometry, CrsReference.epsg(epsg),
                    cn.superhuang.data.scalpel.contract.type.CoordinateDimension.XY));
        }
        PlatformDataType scalar = normalized.contains("boolean") ? PlatformDataType.BOOLEAN
                : normalized.contains("long") || normalized.contains("integer") ? PlatformDataType.LONG
                : normalized.contains("int") || normalized.contains("short") ? PlatformDataType.INTEGER
                : normalized.contains("decimal") ? PlatformDataType.DECIMAL
                : normalized.contains("double") || normalized.contains("float") ? PlatformDataType.DOUBLE
                : normalized.contains("date") || normalized.contains("time") ? PlatformDataType.TIMESTAMP
                : PlatformDataType.STRING;
        return scalar == PlatformDataType.DECIMAL
                ? new CanvasColumnSchema(name, scalar, null, 38, 10, nullable, null, false, false, null)
                : new CanvasColumnSchema(name, scalar, scalar == PlatformDataType.STRING ? 4000 : null,
                null, null, nullable, null, false, false, null);
    }

    private static CanvasColumnSchema arcGisField(JsonNode field) {
        String name = field.path("name").asText(null);
        if (name == null || name.isBlank()) return null;
        String type = field.path("type").asText("").toLowerCase(Locale.ROOT);
        PlatformDataType mapped = switch (type) {
            case "esrifieldtypeoid", "esrifieldtypeinteger", "esrifieldtypesmallinteger" -> PlatformDataType.INTEGER;
            case "esrifieldtypebiginteger" -> PlatformDataType.LONG;
            case "esrifieldtypesingle" -> PlatformDataType.FLOAT;
            case "esrifieldtypedouble" -> PlatformDataType.DOUBLE;
            case "esrifieldtypedate", "esrifieldtypetimestampoffset", "esrifieldtypedateonly", "esrifieldtypetimeonly" -> PlatformDataType.TIMESTAMP;
            case "esrifieldtypeguid", "esrifieldtypeglobalid", "esrifieldtypestring" -> PlatformDataType.STRING;
            default -> null;
        };
        if (mapped == null) return null;
        Integer length = mapped == PlatformDataType.STRING ? Math.max(1, Math.min(4000, field.path("length").asInt(4000))) : null;
        return new CanvasColumnSchema(name, mapped, length, null, null, field.path("nullable").asBoolean(true),
                null, false, false, field.path("alias").asText(null));
    }

    private static GeometryKind arcGisGeometry(String type) {
        return switch (type == null ? "" : type) {
            case "esriGeometryPoint" -> GeometryKind.POINT;
            case "esriGeometryMultipoint" -> GeometryKind.MULTIPOINT;
            case "esriGeometryPolyline" -> GeometryKind.MULTILINESTRING;
            case "esriGeometryPolygon" -> GeometryKind.MULTIPOLYGON;
            default -> null;
        };
    }

    private String get(HttpApiContracts.RuntimeConnection connection, String url) {
        try {
            RequestTarget target = requestTarget(connection, url);
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(target.url()))
                    .GET().timeout(Duration.ofMillis(connection.configuration().requestTimeoutMs()));
            target.headers().forEach(request::header);
            HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String body = response.body();
            if (body != null && body.length() > MAX_RESPONSE_CHARS) throw remote("SPATIAL_RESPONSE_TOO_LARGE", "空间服务响应超过管理端限制");
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw remote("SPATIAL_HTTP_ERROR", "空间服务返回 HTTP " + response.statusCode());
            }
            return body == null ? "" : body;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw remote("SPATIAL_SERVICE_CONNECTION_FAILED", "连接空间服务失败：" + safeMessage(exception));
        }
    }

    private static RequestTarget requestTarget(HttpApiContracts.RuntimeConnection connection, String url) {
        Map<String, String> headers = new LinkedHashMap<>();
        connection.configuration().defaultHeaders().forEach(header -> headers.put(header.name(), header.value()));
        String result = url;
        HttpApiContracts.AuthenticationConfiguration auth = connection.configuration().authentication();
        HttpApiContracts.CredentialBundle credentials = connection.credentials();
        switch (auth) {
            case HttpApiContracts.NoneAuthentication ignored -> { }
            case HttpApiContracts.BasicAuthentication basic -> headers.put("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString((basic.username() + ":" + requireCredential(credentials.password(), "Basic 密码"))
                            .getBytes(StandardCharsets.UTF_8)));
            case HttpApiContracts.BearerTokenAuthentication ignored -> headers.put("Authorization", "Bearer "
                    + requireCredential(credentials.bearerToken(), "Bearer Token"));
            case HttpApiContracts.ApiKeyAuthentication key -> {
                String value = key.valueTemplate().replace("${credential.apiKey}", requireCredential(credentials.apiKey(), "API Key"));
                if (key.location() == HttpApiContracts.ValueLocation.HEADER) headers.put(key.name(), value);
                else if (key.location() == HttpApiContracts.ValueLocation.QUERY) result = withQuery(result, Map.of(key.name(), value));
                else throw remote("SPATIAL_AUTH_CONFIGURATION_UNSUPPORTED", "空间服务 GET 不支持 Body API Key");
            }
            case HttpApiContracts.OAuth2ClientCredentialsAuthentication ignored -> throw remote(
                    "SPATIAL_AUTH_CONFIGURATION_UNSUPPORTED", "空间服务暂不支持 OAuth2 Client Credentials 鉴权");
            case HttpApiContracts.TokenEndpointAuthentication ignored -> throw remote(
                    "SPATIAL_AUTH_CONFIGURATION_UNSUPPORTED", "空间服务暂不支持运行时 Token 鉴权");
        }
        return new RequestTarget(result, Map.copyOf(headers));
    }

    private JsonNode json(String value) {
        try { return objectMapper.readTree(value); }
        catch (RuntimeException exception) { throw remote("SPATIAL_INVALID_JSON", "空间服务未返回有效 JSON"); }
    }

    private static XMLStreamReader xml(String value) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        return factory.createXMLStreamReader(new StringReader(value));
    }

    private static String capabilityVersion(String body) {
        try {
            XMLStreamReader reader = xml(body);
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamConstants.START_ELEMENT && "WFS_Capabilities".equals(reader.getLocalName())) {
                    String version = reader.getAttributeValue(null, "version");
                    if (version != null && !version.isBlank()) return version;
                }
            }
        } catch (XMLStreamException exception) {
            throw remote("WFS_INVALID_CAPABILITIES", "无法解析 WFS Capabilities");
        }
        throw remote("WFS_INVALID_CAPABILITIES", "响应不是有效 WFS Capabilities");
    }

    private static void requireArcGisSuccess(JsonNode value) {
        if (value.has("error")) throw remote("ARCGIS_REMOTE_ERROR", arcGisError(value));
    }

    private static String arcGisError(JsonNode value) {
        JsonNode error = value.path("error");
        return error.path("message").asText("ArcGIS 服务返回错误");
    }

    private static Integer epsg(JsonNode reference) {
        if (reference == null || reference.isMissingNode()) return null;
        int latest = reference.path("latestWkid").asInt(0);
        int wkid = latest > 0 ? latest : reference.path("wkid").asInt(0);
        return wkid > 0 ? wkid : null;
    }

    private static Integer epsg(String crs) {
        if (crs == null) return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?i)(?:EPSG[:/]|::)(\\d+)$").matcher(crs.trim());
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private static String withQuery(String url, Map<String, String> parameters) {
        URI uri = URI.create(url);
        StringBuilder query = new StringBuilder(uri.getRawQuery() == null ? "" : uri.getRawQuery());
        parameters.forEach((key, value) -> {
            if (query.length() > 0) query.append('&');
            query.append(URLEncoder.encode(key, StandardCharsets.UTF_8)).append('=')
                    .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        });
        try { return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), query.toString(), null).toString(); }
        catch (Exception exception) { throw remote("SPATIAL_URL_INVALID", "空间服务地址不合法"); }
    }

    private static Map<String, Object> jsonObject(JsonNode value) {
        if (value == null || !value.isObject()) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> field : value.properties()) {
            result.put(field.getKey(), jsonValue(field.getValue()));
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    private static Object jsonValue(JsonNode value) {
        if (value == null || value.isNull()) return null;
        if (value.isTextual()) return value.asText();
        if (value.isBoolean()) return value.asBoolean();
        if (value.isIntegralNumber()) return value.asLong();
        if (value.isFloatingPointNumber()) return value.asDouble();
        return value.toString();
    }

    private static String wfsUrl(String url, Map<String, String> parameters) { return withQuery(url, parameters); }

    private static String resolve(HttpApiContracts.RuntimeConnection connection, String identifier) {
        if (identifier.startsWith("http://") || identifier.startsWith("https://")) {
            URI base = URI.create(connection.configuration().baseUrl());
            URI candidate = URI.create(identifier);
            if (!sameOrigin(base, candidate)) throw remote("SPATIAL_REMOTE_IDENTIFIER_REJECTED", "远程资源必须与数据源同源");
            return identifier;
        }
        return connection.configuration().baseUrl().replaceFirst("/+$", "") + "/" + identifier.replaceFirst("^/+", "");
    }

    private static boolean sameOrigin(URI first, URI second) {
        return java.util.Objects.equals(first.getScheme(), second.getScheme())
                && java.util.Objects.equals(first.getHost(), second.getHost()) && first.getPort() == second.getPort();
    }

    private static String relative(String value, String base) {
        String normalizedBase = base.replaceFirst("/+$", "");
        return value.startsWith(normalizedBase) ? value.substring(normalizedBase.length()).replaceFirst("^/+", "") : value;
    }

    private static String trimSlash(String value) { return value == null ? "" : value.replaceFirst("/+$", ""); }
    private static String requireCredential(String value, String label) {
        if (value == null || value.isBlank()) throw remote("SPATIAL_AUTHENTICATION_FAILED", label + "未配置");
        return value;
    }
    private static String safeMessage(Throwable exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
    private static ResponseStatusException remote(String code, String detail) {
        ResponseStatusException exception = new ResponseStatusException(HttpStatus.BAD_GATEWAY, detail);
        exception.getBody().setProperty("code", code);
        return exception;
    }

    private record RequestTarget(String url, Map<String, String> headers) { }
    public record SpatialProbe(String product, String version) { }
    public record SpatialPreview(List<Map<String, Object>> rows, boolean truncated) { }
}
