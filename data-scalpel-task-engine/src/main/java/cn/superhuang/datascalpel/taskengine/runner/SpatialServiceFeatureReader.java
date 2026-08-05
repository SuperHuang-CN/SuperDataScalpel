package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.httpapi.HttpApiContracts;
import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.SpatialServiceProtocol;
import cn.superhuang.data.scalpel.contract.task.SpatialServiceResourceDefinition;
import cn.superhuang.datascalpel.taskengine.http.JsonSupport;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.sql.sedona_sql.expressions.st_constructors;

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
import java.util.Map;

/** Bounded, explicit GeoJSON reader for ArcGIS FeatureServer and OGC WFS. */
final class SpatialServiceFeatureReader {
    private static final int PAGE_SIZE = 2_000;
    private static final int MAX_PAGES = 10_000;
    private static final int MAX_RESPONSE_CHARS = 32 * 1024 * 1024;
    private final ObjectMapper mapper = JsonSupport.strictObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    Dataset<Row> read(
            SparkSession spark,
            HttpApiContracts.RuntimeConnection connection,
            SpatialServiceResourceDefinition resource,
            CanvasTableSchema schema,
            String nodeId
    ) {
        try {
            List<String> features = switch (resource.protocol()) {
                case ARCGIS_REST -> readArcGis(connection, resource);
                case WFS -> readWfs(connection, resource);
            };
            return toDataset(spark, features, schema, resource.geometryFieldName());
        } catch (RunnerExecutionException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RunnerExecutionException("SPATIAL_SERVICE_INPUT_FAILED", "空间服务要素读取失败", nodeId, exception);
        }
    }

    private List<String> readArcGis(HttpApiContracts.RuntimeConnection connection, SpatialServiceResourceDefinition resource) {
        List<String> features = new ArrayList<>();
        int offset = 0;
        for (int page = 0; page < MAX_PAGES; page++) {
            String url = join(connection.configuration().baseUrl(), resource.remoteIdentifier()) + "/query";
            Map<String, String> query = new LinkedHashMap<>();
            query.put("f", "geojson"); query.put("where", "1=1"); query.put("outFields", "*");
            query.put("returnGeometry", "true"); query.put("resultOffset", Integer.toString(offset));
            query.put("resultRecordCount", Integer.toString(PAGE_SIZE));
            if (resource.epsgCode() != null) query.put("outSR", resource.epsgCode().toString());
            JsonNode root = json(get(connection, withQuery(url, query)));
            failArcGis(root);
            JsonNode pageFeatures = root.path("features");
            for (JsonNode feature : pageFeatures) features.add(feature.toString());
            if (pageFeatures.size() < PAGE_SIZE && !root.path("exceededTransferLimit").asBoolean(false)) return List.copyOf(features);
            offset += pageFeatures.size();
            if (pageFeatures.isEmpty()) return List.copyOf(features);
        }
        throw new RunnerExecutionException("SPATIAL_MAX_PAGES_EXCEEDED", "ArcGIS 图层页数超过运行上限", null);
    }

    private List<String> readWfs(HttpApiContracts.RuntimeConnection connection, SpatialServiceResourceDefinition resource) {
        List<String> features = new ArrayList<>();
        int start = 0;
        boolean v2 = resource.wfsVersion() != null && resource.wfsVersion().startsWith("2.");
        for (int page = 0; page < MAX_PAGES; page++) {
            Map<String, String> query = new LinkedHashMap<>();
            query.put("service", "WFS"); query.put("version", resource.wfsVersion()); query.put("request", "GetFeature");
            query.put(v2 ? "typeNames" : "typeName", resource.remoteIdentifier());
            query.put(v2 ? "count" : "maxFeatures", Integer.toString(PAGE_SIZE));
            if (v2) query.put("startIndex", Integer.toString(start));
            query.put("outputFormat", "application/json");
            JsonNode root = json(get(connection, withQuery(connection.configuration().baseUrl(), query)));
            if (!"FeatureCollection".equals(root.path("type").asText())) {
                throw new RunnerExecutionException("WFS_RESPONSE_FORMAT_UNSUPPORTED", "WFS 未返回 GeoJSON FeatureCollection", null);
            }
            JsonNode pageFeatures = root.path("features");
            for (JsonNode feature : pageFeatures) features.add(feature.toString());
            if (pageFeatures.size() < PAGE_SIZE || !v2) return List.copyOf(features);
            start += pageFeatures.size();
        }
        throw new RunnerExecutionException("SPATIAL_MAX_PAGES_EXCEEDED", "WFS 要素页数超过运行上限", null);
    }

    private Dataset<Row> toDataset(
            SparkSession spark, List<String> features, CanvasTableSchema schema, String geometryFieldName
    ) {
        List<CanvasColumnSchema> columns = schema.columns();
        StructType rawSchema = SparkTypeMapper.toStructType(columns.stream().map(column ->
                column.fieldType() == cn.superhuang.data.scalpel.contract.type.PlatformDataType.GEOMETRY
                        ? new CanvasColumnSchema(column.name(), cn.superhuang.data.scalpel.contract.type.PlatformDataType.STRING,
                        null, null, null, column.nullable(), null, false, false, column.comment()) : column).toList());
        List<String> rows = new ArrayList<>(features.size());
        for (String featureJson : features) {
            JsonNode feature = json(featureJson);
            ObjectNode row = mapper.createObjectNode();
            JsonNode properties = feature.path("properties");
            for (CanvasColumnSchema column : columns) {
                if (column.fieldType() == cn.superhuang.data.scalpel.contract.type.PlatformDataType.GEOMETRY) {
                    row.set(column.name(), feature.path("geometry"));
                } else if (properties.has(column.name())) {
                    row.set(column.name(), properties.get(column.name()));
                }
            }
            rows.add(row.toString());
        }
        Dataset<Row> dataset = spark.read().schema(rawSchema).json(spark.createDataset(rows, Encoders.STRING()));
        for (CanvasColumnSchema column : columns) {
            if (column.fieldType() == cn.superhuang.data.scalpel.contract.type.PlatformDataType.GEOMETRY) {
                dataset = dataset.withColumn(column.name(), st_constructors.ST_GeomFromGeoJSON(dataset.col(column.name())));
            }
        }
        Dataset<Row> completed = dataset;
        Column[] selected = columns.stream().map(column -> completed.col(column.name()).alias(column.name())).toArray(Column[]::new);
        return completed.select(selected);
    }

    private String get(HttpApiContracts.RuntimeConnection connection, String url) {
        try {
            RequestTarget target = requestTarget(connection, url);
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(target.url())).GET()
                    .timeout(Duration.ofMillis(connection.configuration().requestTimeoutMs()));
            target.headers().forEach(request::header);
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RunnerExecutionException("SPATIAL_HTTP_ERROR", "空间服务返回 HTTP " + response.statusCode(), null);
            }
            if (response.body() != null && response.body().length() > MAX_RESPONSE_CHARS) {
                throw new RunnerExecutionException("SPATIAL_RESPONSE_TOO_LARGE", "空间服务单页响应超过运行限制", null);
            }
            return response.body() == null ? "" : response.body();
        } catch (RunnerExecutionException exception) { throw exception; }
        catch (Exception exception) { throw new RunnerExecutionException("SPATIAL_NETWORK_ERROR", "空间服务网络请求失败", null, exception); }
    }

    private static RequestTarget requestTarget(HttpApiContracts.RuntimeConnection connection, String url) {
        Map<String, String> headers = new LinkedHashMap<>();
        connection.configuration().defaultHeaders().forEach(header -> headers.put(header.name(), header.value()));
        HttpApiContracts.AuthenticationConfiguration auth = connection.configuration().authentication();
        HttpApiContracts.CredentialBundle credentials = connection.credentials();
        String result = url;
        switch (auth) {
            case HttpApiContracts.NoneAuthentication ignored -> { }
            case HttpApiContracts.BasicAuthentication basic -> headers.put("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                    (basic.username() + ":" + required(credentials.password(), "Basic 密码")).getBytes(StandardCharsets.UTF_8)));
            case HttpApiContracts.BearerTokenAuthentication ignored -> headers.put("Authorization", "Bearer " + required(credentials.bearerToken(), "Bearer Token"));
            case HttpApiContracts.ApiKeyAuthentication key -> {
                String value = key.valueTemplate().replace("${credential.apiKey}", required(credentials.apiKey(), "API Key"));
                if (key.location() == HttpApiContracts.ValueLocation.HEADER) headers.put(key.name(), value);
                else if (key.location() == HttpApiContracts.ValueLocation.QUERY) result = withQuery(result, Map.of(key.name(), value));
                else throw new RunnerExecutionException("SPATIAL_AUTH_UNSUPPORTED", "空间服务不支持 Body API Key", null);
            }
            case HttpApiContracts.OAuth2ClientCredentialsAuthentication ignored -> throw new RunnerExecutionException("SPATIAL_AUTH_UNSUPPORTED", "空间服务暂不支持 OAuth2 Client Credentials", null);
            case HttpApiContracts.TokenEndpointAuthentication ignored -> throw new RunnerExecutionException("SPATIAL_AUTH_UNSUPPORTED", "空间服务暂不支持运行时 Token 鉴权", null);
        }
        return new RequestTarget(result, Map.copyOf(headers));
    }

    private JsonNode json(String value) {
        try { return mapper.readTree(value); }
        catch (Exception exception) { throw new RunnerExecutionException("SPATIAL_INVALID_JSON", "空间服务未返回有效 JSON", null, exception); }
    }
    private static void failArcGis(JsonNode root) {
        if (root.has("error")) throw new RunnerExecutionException("ARCGIS_REMOTE_ERROR", root.path("error").path("message").asText("ArcGIS 服务返回错误"), null);
        if (!"FeatureCollection".equals(root.path("type").asText())) throw new RunnerExecutionException("ARCGIS_RESPONSE_FORMAT_UNSUPPORTED", "ArcGIS 图层未返回 GeoJSON FeatureCollection", null);
    }
    private static String join(String base, String path) { return base.replaceFirst("/+$", "") + "/" + path.replaceFirst("^/+", ""); }
    private static String withQuery(String url, Map<String, String> query) {
        URI uri = URI.create(url); StringBuilder value = new StringBuilder(uri.getRawQuery() == null ? "" : uri.getRawQuery());
        query.forEach((key, item) -> { if (value.length() > 0) value.append('&'); value.append(URLEncoder.encode(key, StandardCharsets.UTF_8)).append('=').append(URLEncoder.encode(item, StandardCharsets.UTF_8)); });
        try { return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), value.toString(), null).toString(); }
        catch (Exception exception) { throw new RunnerExecutionException("SPATIAL_URL_INVALID", "空间服务地址不合法", null, exception); }
    }
    private static String required(String value, String name) { if (value == null || value.isBlank()) throw new RunnerExecutionException("SPATIAL_AUTHENTICATION_FAILED", name + "未配置", null); return value; }
    private record RequestTarget(String url, Map<String, String> headers) { }
}
