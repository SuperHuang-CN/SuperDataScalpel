package cn.superhuang.data.scalpel.business.service;

import cn.superhuang.data.scalpel.business.service.domain.ServiceEngine;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
                    "defaultStyle", Map.of("name", spec.styleName())
            ));
            client.put().uri("/rest/layers/{workspace}:{layer}", workspace, publishedName)
                    .contentType(MediaType.APPLICATION_JSON).body(layer).retrieve().toBodilessEntity();
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

    public static String storeName(UUID dataSourceId) {
        return "ds_" + Objects.requireNonNull(dataSourceId).toString().replace("-", "");
    }

    public static String layerName(String serviceCode) {
        return "svc_" + ServiceEngine.normalizeCode(serviceCode);
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
}
