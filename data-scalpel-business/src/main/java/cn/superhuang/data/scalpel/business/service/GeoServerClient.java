package cn.superhuang.data.scalpel.business.service;

import cn.superhuang.data.scalpel.business.service.domain.ServiceEngine;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Minimal GeoServer 3 REST client used directly by the Admin control plane. */
@Component
public class GeoServerClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private final ServiceEngineCredentialCipher credentialCipher;

    public GeoServerClient(ServiceEngineCredentialCipher credentialCipher) {
        this.credentialCipher = credentialCipher;
    }

    public Discovery discover(
            String code,
            String adminUrl,
            String runtimeUrl,
            String username,
            String password,
            String workspace,
            boolean initializeWorkspace
    ) {
        try {
            String normalizedCode = ServiceEngine.normalizeCode(code);
            String normalizedWorkspace = ServiceEngine.normalizeWorkspace(workspace);
            String resolvedAdminUrl = resolveBaseUrl(adminUrl, username, password);
            String resolvedRuntimeUrl = resolveRuntimeUrl(runtimeUrl, resolvedAdminUrl);
            RestClient admin = client(resolvedAdminUrl, username, password);
            String version = version(admin);
            requirePostGis(admin);
            if (initializeWorkspace) ensureWorkspace(admin, normalizedWorkspace);
            verifyCapabilities(resolvedRuntimeUrl, normalizedWorkspace);
            return new Discovery(
                    normalizedCode, version, resolvedAdminUrl, resolvedRuntimeUrl,
                    normalizedWorkspace, List.of("POSTGRESQL"), List.of("WMS", "WFS")
            );
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    public Discovery discover(ServiceEngine engine) {
        return discover(
                engine.getCode(), engine.getAdminUrl(), engine.getRuntimeUrl(),
                engine.getGeoServerUsername(), credentialCipher.decrypt(engine.getGeoServerPasswordCiphertext()),
                engine.getGeoServerWorkspace(), false
        );
    }

    public void upsertDataStore(ServiceEngine engine, DataStoreSpec spec) {
        try {
            RestClient client = client(engine);
            String workspace = engine.getGeoServerWorkspace();
            String store = storeName(spec.dataSourceId());
            Map<String, Object> payload = dataStorePayload(workspace, store, spec);
            if (dataStoreExists(client, workspace, store)) {
                client.put().uri("/rest/workspaces/{workspace}/datastores/{store}", workspace, store)
                        .contentType(MediaType.APPLICATION_JSON).body(payload).retrieve().toBodilessEntity();
            } else {
                client.post().uri("/rest/workspaces/{workspace}/datastores", workspace)
                        .contentType(MediaType.APPLICATION_JSON).body(payload).retrieve().toBodilessEntity();
            }
            testDataStore(engine, spec.dataSourceId());
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    public void testDataStore(ServiceEngine engine, UUID dataSourceId) {
        try {
            client(engine).get()
                    .uri(builder -> builder
                            .path("/rest/workspaces/{workspace}/datastores/{store}/featuretypes.json")
                            .queryParam("list", "available")
                            .build(engine.getGeoServerWorkspace(), storeName(dataSourceId)))
                    .retrieve().toBodilessEntity();
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    public void removeDataStore(ServiceEngine engine, UUID dataSourceId) {
        try {
            client(engine).delete()
                    .uri(builder -> builder
                            .path("/rest/workspaces/{workspace}/datastores/{store}")
                            .queryParam("recurse", true)
                            .build(engine.getGeoServerWorkspace(), storeName(dataSourceId)))
                    .retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.NotFound ignored) {
            // Idempotent removal.
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    public void upsertLayer(ServiceEngine engine, LayerSpec spec) {
        try {
            RestClient client = client(engine);
            String workspace = engine.getGeoServerWorkspace();
            String store = storeName(spec.dataSourceId());
            String publishedName = layerName(spec.serviceCode());
            Map<String, Object> featureType = Map.of("featureType", mapOf(
                    "name", publishedName,
                    "nativeName", spec.table(),
                    "title", spec.title(),
                    "srs", "EPSG:" + spec.epsg(),
                    "projectionPolicy", "FORCE_DECLARED",
                    "enabled", true
            ));
            if (featureTypeExists(client, workspace, store, publishedName)) {
                client.put().uri(builder -> builder
                                .path("/rest/workspaces/{workspace}/datastores/{store}/featuretypes/{featureType}")
                                .queryParam("recalculate", "nativebbox,latlonbbox")
                                .build(workspace, store, publishedName))
                        .contentType(MediaType.APPLICATION_JSON).body(featureType).retrieve().toBodilessEntity();
            } else {
                client.post().uri(builder -> builder
                                .path("/rest/workspaces/{workspace}/datastores/{store}/featuretypes")
                                .queryParam("recalculate", "nativebbox,latlonbbox")
                                .build(workspace, store))
                        .contentType(MediaType.APPLICATION_JSON).body(featureType).retrieve().toBodilessEntity();
            }
            Map<String, Object> layer = Map.of("layer", Map.of(
                    "enabled", true,
                    "defaultStyle", Map.of("name", qualifiedStyleName(workspace, spec.styleName()))
            ));
            client.put().uri("/rest/layers/{workspace}:{layer}", workspace, publishedName)
                    .contentType(MediaType.APPLICATION_JSON).body(layer).retrieve().toBodilessEntity();
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    public void upsertStyle(ServiceEngine engine, String styleName, String sldText) {
        try {
            RestClient client = client(engine);
            String workspace = engine.getGeoServerWorkspace();
            if (styleExists(client, workspace, styleName)) {
                client.put().uri("/rest/workspaces/{workspace}/styles/{style}", workspace, styleName)
                        .contentType(MediaType.parseMediaType("application/vnd.ogc.sld+xml"))
                        .body(sldText)
                        .retrieve().toBodilessEntity();
            } else {
                client.post().uri(builder -> builder
                                .path("/rest/workspaces/{workspace}/styles")
                                .queryParam("name", styleName)
                                .build(workspace))
                        .contentType(MediaType.parseMediaType("application/vnd.ogc.sld+xml"))
                        .body(sldText)
                        .retrieve().toBodilessEntity();
            }
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    public void bindLayerStyle(ServiceEngine engine, String serviceCode, String styleName) {
        try {
            String workspace = engine.getGeoServerWorkspace();
            Map<String, Object> layer = Map.of("layer", Map.of(
                    "enabled", true,
                    "defaultStyle", Map.of("name", qualifiedStyleName(workspace, styleName))
            ));
            client(engine).put().uri("/rest/layers/{workspace}:{layer}", workspace, layerName(serviceCode))
                    .contentType(MediaType.APPLICATION_JSON).body(layer).retrieve().toBodilessEntity();
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    public void removeStyle(ServiceEngine engine, String serviceCode) {
        try {
            client(engine).delete().uri(builder -> builder
                            .path("/rest/workspaces/{workspace}/styles/{style}")
                            .queryParam("purge", true)
                            .build(engine.getGeoServerWorkspace(), styleName(serviceCode)))
                    .retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.NotFound ignored) {
            // Idempotent removal.
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    public void removeLayer(ServiceEngine engine, UUID dataSourceId, String serviceCode) {
        try {
            client(engine).delete()
                    .uri(builder -> builder
                            .path("/rest/workspaces/{workspace}/datastores/{store}/featuretypes/{featureType}")
                            .queryParam("recurse", true)
                            .build(
                                    engine.getGeoServerWorkspace(), storeName(dataSourceId), layerName(serviceCode)
                            ))
                    .retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.NotFound ignored) {
            // Idempotent removal.
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    public Optional<LayerPreview> inspectLayer(ServiceEngine engine, UUID dataSourceId, String serviceCode) {
        try {
            RestClient client = client(engine);
            String workspace = engine.getGeoServerWorkspace();
            String store = storeName(dataSourceId);
            String layer = layerName(serviceCode);
            if (!featureTypeExists(client, workspace, store, layer) || !layerExists(client, workspace, layer)) {
                return Optional.empty();
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> body = client.get()
                    .uri(
                            "/rest/workspaces/{workspace}/datastores/{store}/featuretypes/{featureType}.json",
                            workspace, store, layer
                    )
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Map.class);
            return Optional.of(new LayerPreview(latLonBounds(body)));
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    public byte[] renderWms(
            ServiceEngine engine,
            String qualifiedLayerName,
            String bbox,
            int width,
            int height
    ) {
        try {
            RestClient runtime = RestClient.builder()
                    .baseUrl(engine.getRuntimeUrl())
                    .requestFactory(requestFactory())
                    .build();
            ResponseEntity<byte[]> response = runtime.get()
                    .uri(builder -> builder
                            .path("/{workspace}/wms")
                            .queryParam("service", "WMS")
                            .queryParam("version", "1.3.0")
                            .queryParam("request", "GetMap")
                            .queryParam("layers", qualifiedLayerName)
                            .queryParam("styles", "")
                            .queryParam("crs", "EPSG:3857")
                            .queryParam("scaleMethod", "OGC")
                            .queryParam("bbox", bbox)
                            .queryParam("width", width)
                            .queryParam("height", height)
                            .queryParam("format", "image/png")
                            .queryParam("transparent", true)
                            .build(engine.getGeoServerWorkspace()))
                    .accept(MediaType.IMAGE_PNG)
                    .retrieve()
                    .toEntity(byte[].class);
            return requirePng(response, "GeoServer WMS 未返回有效 PNG 图片，请检查图层发布状态");
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "GeoServer WMS 要求认证，当前空间服务无法公开预览",
                    exception
            );
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    public byte[] renderWms(
            ServiceEngine engine,
            String qualifiedLayerName,
            String bbox,
            int width,
            int height,
            String sldBody
    ) {
        try {
            RestClient runtime = RestClient.builder()
                    .baseUrl(engine.getRuntimeUrl())
                    .requestFactory(requestFactory())
                    .build();
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("service", "WMS");
            form.add("version", "1.3.0");
            form.add("request", "GetMap");
            form.add("layers", qualifiedLayerName);
            form.add("styles", "");
            form.add("crs", "EPSG:3857");
            form.add("scaleMethod", "OGC");
            form.add("bbox", bbox);
            form.add("width", Integer.toString(width));
            form.add("height", Integer.toString(height));
            form.add("format", "image/png");
            form.add("transparent", "true");
            form.add("SLD_BODY", sldBody);
            ResponseEntity<byte[]> response = runtime.post()
                    .uri("/{workspace}/wms", engine.getGeoServerWorkspace())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.IMAGE_PNG)
                    .body(form)
                    .retrieve()
                    .toEntity(byte[].class);
            return requirePng(response, "GeoServer 未能使用草稿样式渲染 PNG，请检查动态样式配置");
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "GeoServer WMS 不允许动态样式预览",
                    exception
            );
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    public byte[] renderLegend(ServiceEngine engine, String qualifiedLayerName) {
        try {
            RestClient runtime = RestClient.builder()
                    .baseUrl(engine.getRuntimeUrl())
                    .requestFactory(requestFactory())
                    .build();
            ResponseEntity<byte[]> response = runtime.get()
                    .uri(builder -> builder.path("/{workspace}/wms")
                            .queryParam("service", "WMS")
                            .queryParam("version", "1.1.1")
                            .queryParam("request", "GetLegendGraphic")
                            .queryParam("layer", qualifiedLayerName)
                            .queryParam("format", "image/png")
                            .queryParam("transparent", true)
                            .queryParam("legend_options", "forceLabels:on;fontAntiAliasing:true")
                            .build(engine.getGeoServerWorkspace()))
                    .accept(MediaType.IMAGE_PNG)
                    .retrieve()
                    .toEntity(byte[].class);
            return requirePng(response, "GeoServer 未返回有效图例图片");
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    private static byte[] requirePng(ResponseEntity<byte[]> response, String message) {
        MediaType contentType = response.getHeaders().getContentType();
        byte[] body = response.getBody();
        if (contentType == null || !MediaType.IMAGE_PNG.isCompatibleWith(contentType)
                || body == null || body.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, message);
        }
        return body;
    }

    public static String storeName(UUID dataSourceId) {
        return "ds_" + Objects.requireNonNull(dataSourceId).toString().replace("-", "");
    }

    public static String layerName(String serviceCode) {
        return "svc_" + ServiceEngine.normalizeCode(serviceCode);
    }

    public static String styleName(String serviceCode) {
        return "sty_" + layerName(serviceCode);
    }

    private static String qualifiedStyleName(String workspace, String styleName) {
        return "generic".equals(styleName) ? styleName : workspace + ":" + styleName;
    }

    private String resolveBaseUrl(String value, String username, String password) {
        String base = ServiceEngine.normalizeAdminUrl(value);
        List<String> candidates = new ArrayList<>();
        candidates.add(base);
        if (!base.toLowerCase(Locale.ROOT).endsWith("/geoserver")) candidates.add(base + "/geoserver");
        RuntimeException last = null;
        for (String candidate : candidates) {
            try {
                version(client(candidate, username, password));
                return candidate;
            } catch (HttpClientErrorException.NotFound exception) {
                last = exception;
            } catch (RuntimeException exception) {
                last = exception;
                if (exception instanceof RestClientResponseException response
                        && response.getStatusCode().value() != 404) throw translate(exception);
            }
        }
        throw translate(last == null ? new IllegalStateException("未找到 GeoServer REST API") : last);
    }

    private static String resolveRuntimeUrl(String value, String resolvedAdminUrl) {
        String runtime = ServiceEngine.normalizeRuntimeUrl(value);
        if (runtime.toLowerCase(Locale.ROOT).endsWith("/geoserver")) return runtime;
        if (!runtime.contains("/geoserver") && resolvedAdminUrl.endsWith("/geoserver")) return runtime + "/geoserver";
        return runtime;
    }

    private static String version(RestClient client) {
        @SuppressWarnings("unchecked")
        Map<String, Object> body = client.get().uri("/rest/about/version.json")
                .accept(MediaType.APPLICATION_JSON).retrieve().body(Map.class);
        Object about = body == null ? null : body.get("about");
        if (!(about instanceof Map<?, ?> aboutMap) || !(aboutMap.get("resource") instanceof List<?> resources)) {
            throw new IllegalStateException("GeoServer 返回的版本信息不完整");
        }
        return resources.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .filter(item -> "GeoServer".equals(String.valueOf(item.get("@name"))))
                .map(item -> String.valueOf(item.get("Version")))
                .filter(value -> !value.isBlank() && !"null".equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("GeoServer 返回的版本信息不完整"));
    }

    private static void requirePostGis(RestClient client) {
        String manifest = client.get().uri("/rest/about/manifest.json")
                .accept(MediaType.APPLICATION_JSON).retrieve().body(String.class);
        if (manifest == null || !manifest.toLowerCase(Locale.ROOT).contains("postgis")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "GeoServer 未安装 PostGIS DataStore 扩展");
        }
    }

    private static void ensureWorkspace(RestClient client, String workspace) {
        try {
            client.get().uri("/rest/workspaces/{workspace}.json", workspace).retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.NotFound exception) {
            client.post().uri("/rest/workspaces")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("workspace", Map.of("name", workspace)))
                    .retrieve().toBodilessEntity();
        }
        Map<String, Object> wfs = Map.of("wfs", Map.of(
                "name", "WFS",
                "enabled", true,
                "serviceLevel", "BASIC"
        ));
        client.put().uri("/rest/services/wfs/workspaces/{workspace}/settings", workspace)
                .contentType(MediaType.APPLICATION_JSON).body(wfs).retrieve().toBodilessEntity();
    }

    private static void verifyCapabilities(String runtimeUrl, String workspace) {
        RestClient runtime = RestClient.builder().baseUrl(runtimeUrl)
                .requestFactory(requestFactory())
                .build();
        verifyCapability(runtime, workspace, "wms", "WMS", "1.3.0");
        verifyCapability(runtime, workspace, "wfs", "WFS", "2.0.0");
    }

    private static void verifyCapability(
            RestClient runtime,
            String workspace,
            String path,
            String service,
            String version
    ) {
        try {
            runtime.get().uri(builder -> builder.path("/{workspace}/{path}")
                            .queryParam("service", service).queryParam("version", version)
                            .queryParam("request", "GetCapabilities").build(workspace, path))
                    .retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.NotFound exception) {
            // A connection test may run before the dedicated Workspace is created.
            runtime.get().uri(builder -> builder.path("/{path}")
                            .queryParam("service", service).queryParam("version", version)
                            .queryParam("request", "GetCapabilities").build(path))
                    .retrieve().toBodilessEntity();
        }
    }

    private RestClient client(ServiceEngine engine) {
        return client(
                engine.getAdminUrl(), engine.getGeoServerUsername(),
                credentialCipher.decrypt(engine.getGeoServerPasswordCiphertext())
        );
    }

    private static RestClient client(String baseUrl, String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GeoServer 用户名和密码不能为空");
        }
        return RestClient.builder().baseUrl(baseUrl)
                .requestFactory(requestFactory())
                .defaultHeaders(headers -> headers.setBasicAuth(
                        username.trim(), password, StandardCharsets.UTF_8
                ))
                .build();
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return requestFactory;
    }

    private static boolean dataStoreExists(RestClient client, String workspace, String store) {
        try {
            client.get().uri("/rest/workspaces/{workspace}/datastores/{store}.json", workspace, store)
                    .retrieve().toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound exception) {
            return false;
        }
    }

    private static boolean featureTypeExists(
            RestClient client,
            String workspace,
            String store,
            String featureType
    ) {
        try {
            client.get().uri(
                            "/rest/workspaces/{workspace}/datastores/{store}/featuretypes/{featureType}.json",
                            workspace, store, featureType
                    ).retrieve().toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound exception) {
            return false;
        }
    }

    private static boolean layerExists(RestClient client, String workspace, String layer) {
        try {
            client.get().uri("/rest/layers/{workspace}:{layer}.json", workspace, layer)
                    .retrieve().toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound exception) {
            return false;
        }
    }

    private static boolean styleExists(RestClient client, String workspace, String style) {
        try {
            client.get().uri("/rest/workspaces/{workspace}/styles/{style}.json", workspace, style)
                    .retrieve().toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound exception) {
            return false;
        }
    }

    private static List<Double> latLonBounds(Map<String, Object> body) {
        if (body == null || !(body.get("featureType") instanceof Map<?, ?> featureType)
                || !(featureType.get("latLonBoundingBox") instanceof Map<?, ?> bounds)) {
            return List.of();
        }
        Double minX = finiteDouble(bounds.get("minx"));
        Double minY = finiteDouble(bounds.get("miny"));
        Double maxX = finiteDouble(bounds.get("maxx"));
        Double maxY = finiteDouble(bounds.get("maxy"));
        if (minX == null || minY == null || maxX == null || maxY == null
                || minX > maxX || minY > maxY
                || minX < -180 || maxX > 180 || minY < -90 || maxY > 90) {
            return List.of();
        }
        double[] longitude = paddedRange(minX, maxX, -180, 180);
        double[] latitude = paddedRange(minY, maxY, -90, 90);
        return List.of(longitude[0], latitude[0], longitude[1], latitude[1]);
    }

    private static double[] paddedRange(double minimum, double maximum, double lowerLimit, double upperLimit) {
        if (minimum < maximum) return new double[]{minimum, maximum};
        double padding = 0.01d;
        double paddedMinimum = Math.max(lowerLimit, minimum - padding);
        double paddedMaximum = Math.min(upperLimit, maximum + padding);
        return new double[]{paddedMinimum, paddedMaximum};
    }

    private static Double finiteDouble(Object value) {
        if (value == null) return null;
        try {
            double number = value instanceof Number numeric
                    ? numeric.doubleValue()
                    : Double.parseDouble(String.valueOf(value));
            return Double.isFinite(number) ? number : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Map<String, Object> dataStorePayload(
            String workspace,
            String store,
            DataStoreSpec spec
    ) {
        List<Map<String, String>> entries = new ArrayList<>();
        entry(entries, "dbtype", "postgis");
        entry(entries, "host", spec.host());
        entry(entries, "port", Integer.toString(spec.port()));
        entry(entries, "database", spec.database());
        entry(entries, "schema", spec.schema() == null || spec.schema().isBlank() ? "public" : spec.schema());
        entry(entries, "user", spec.username());
        entry(entries, "passwd", spec.password());
        String sslMode = spec.options() == null ? null : spec.options().get("sslmode");
        if (sslMode != null && !sslMode.isBlank()) entry(entries, "SSL mode", sslMode);
        Map<String, Object> dataStore = mapOf(
                "name", store,
                "type", "PostGIS",
                "enabled", true,
                "workspace", Map.of("name", workspace),
                "connectionParameters", Map.of("entry", entries)
        );
        return Map.of("dataStore", dataStore);
    }

    private static void entry(List<Map<String, String>> entries, String key, String value) {
        entries.add(Map.of("@key", key, "$", value == null ? "" : value));
    }

    private static Map<String, Object> mapOf(Object... values) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    public static ResponseStatusException translate(RuntimeException exception) {
        if (exception instanceof ResponseStatusException statusException) return statusException;
        if (exception instanceof RestClientResponseException response) {
            int status = response.getStatusCode().value();
            String detail = status == 401 || status == 403
                    ? "GeoServer 拒绝访问，请检查管理账号和密码"
                    : "GeoServer 返回异常状态：HTTP " + status;
            return new ResponseStatusException(HttpStatus.BAD_GATEWAY, detail, exception);
        }
        String message = exception == null || exception.getMessage() == null
                ? "远程调用失败"
                : exception.getMessage().substring(0, Math.min(300, exception.getMessage().length()));
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GeoServer 不可访问：" + message, exception);
    }

    public record Discovery(
            String code,
            String version,
            String adminUrl,
            String runtimeUrl,
            String workspace,
            List<String> databaseTypes,
            List<String> capabilities
    ) {
    }

    public record DataStoreSpec(
            UUID dataSourceId,
            String host,
            int port,
            String database,
            String schema,
            String username,
            String password,
            Map<String, String> options
    ) {
    }

    public record LayerSpec(
            UUID dataSourceId,
            String serviceCode,
            String title,
            String table,
            String geometryColumn,
            int epsg,
            String styleName
    ) {
    }

    public record LayerPreview(List<Double> initialBounds) {
        public LayerPreview {
            initialBounds = initialBounds == null ? List.of() : List.copyOf(initialBounds);
        }
    }
}
