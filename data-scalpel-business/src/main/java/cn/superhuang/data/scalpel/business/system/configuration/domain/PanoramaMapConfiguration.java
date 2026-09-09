package cn.superhuang.data.scalpel.business.system.configuration.domain;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.util.Set;

/** Typed value of the panorama.map STRING setting; URLs are fetched only by the user's map. */
public record PanoramaMapConfiguration(String url, String attribution, int maxZoom) {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    public static PanoramaMapConfiguration parse(String raw) {
        try {
            JsonNode node = JSON.readTree(raw);
            if (!node.isObject() || node.size() != 3 || !node.has("url") || !node.has("attribution") || !node.has("maxZoom")
                    || !node.get("url").isString() || !node.get("attribution").isString() || !node.get("maxZoom").isIntegralNumber() || !node.get("maxZoom").canConvertToInt()) throw new IllegalArgumentException();
            String url = node.get("url").asString().trim(), attribution = node.get("attribution").asString().trim();
            int zoom = node.get("maxZoom").asInt(-1);
            if (zoom < 0 || zoom > 22 || attribution.length() > 500 || url.length() > 3000) throw new IllegalArgumentException();
            if (!url.isEmpty()) {
                if (!url.contains("{z}") || !url.contains("{x}") || !url.contains("{y}")) throw new IllegalArgumentException();
                URI uri = URI.create(url.replace("{z}", "0").replace("{x}", "0").replace("{y}", "0"));
                if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) throw new IllegalArgumentException();
            }
            return new PanoramaMapConfiguration(url, attribution, zoom);
        } catch (RuntimeException e) { throw new IllegalArgumentException("全景地图配置无效：请填写 HTTP(S) XYZ 地址、纯文本署名和 0～22 的最大缩放级别"); }
    }
    public static String normalize(String raw) { return JSON.writeValueAsString(parse(raw)); }
}
